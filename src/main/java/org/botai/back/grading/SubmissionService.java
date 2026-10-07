package org.botai.back.grading;

import jakarta.validation.constraints.*;
import org.botai.back.attempt.*;
import org.botai.back.catalog.*;
import org.botai.back.common.*;
import org.botai.back.grading.port.GradingGateway;
import org.botai.back.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.botai.back.grading.SubmissionStore.Row;
import static org.botai.back.grading.SubmissionStore.RETRYABLE_STATUSES;

@Service
public class SubmissionService {
    public record Intent(@NotNull UUID attemptId,@NotNull UUID attemptItemId,@Min(0) int inputRevision,UUID retryOfId) { }
    public record Finalize(@Min(0) int expectedRevision) { }
    public record ClientRejection(@Min(0) int expectedRevision,@NotNull String reason) { }
    public record Check(@Min(0) int revision,@Size(max=50) List<UUID> itemIds) { }
    private final SubmissionStore store;private final TransactionTemplate tx;private final CatalogService catalog;private final GradingGateway gateway;private final ProfileService profile;private final AttemptService attempts;private final int queueLimit,dailyLimit;
    public SubmissionService(SubmissionStore store,TransactionTemplate tx,CatalogService catalog,GradingGateway gateway,ProfileService profile,AttemptService attempts,@Value("${app.grading.queue-limit:100}")int queueLimit,@Value("${app.grading.daily-limit:30}")int dailyLimit){this.store=store;this.tx=tx;this.catalog=catalog;this.gateway=gateway;this.profile=profile;this.attempts=attempts;this.queueLimit=queueLimit;this.dailyLimit=dailyLimit;}
    public static void key(String key){if(key==null||key.isBlank()||key.length()>128)throw ApiException.invalid("Нужен ключ повторного запроса");}
    public JsonNode create(UUID user,String key,Intent input){key(key);String hash=store.json().hash(input);
        UUID id=tx.execute(t->{store.db().queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,user);var prior=store.db().queryForList("SELECT id,request_hash FROM grading_submissions WHERE user_id=? AND idempotency_key=?",user,key);if(!prior.isEmpty()){if(!hash.equals(prior.getFirst().get("request_hash")))throw ApiException.conflict("idempotency_conflict");return (UUID)prior.getFirst().get("id");}
            int count=store.db().queryForObject("SELECT count(*) FROM grading_submissions WHERE user_id=? AND created_at>now()-interval '1 hour'",Integer.class,user);if(count>=100)throw new ApiException(429,"submission_rate_limit","Слишком много проверок");
            UUID created=UUID.randomUUID();store.db().update("INSERT INTO grading_submissions(id,user_id,requested_attempt_id,requested_item_id,input_revision,idempotency_key,request_hash,request_id) VALUES(?,?,?,?,?,?,?,?)",created,user,input.attemptId,input.attemptItemId,input.inputRevision,key,hash,UUID.fromString(RequestIds.current()));store.event(created,"intent_created",user,Map.of());return created;});
        tx.executeWithoutResult(t->{Row s=store.row(id,user,true);if(s.version()!=null||!s.status().equals("draft"))return;
            var items=store.db().queryForList("SELECT i.* FROM attempts a JOIN attempt_items i ON i.attempt_id=a.id WHERE a.id=? AND a.user_id=? AND a.deleted_at IS NULL AND i.id=? FOR UPDATE OF a",input.attemptId,user,input.attemptItemId);
            if(items.isEmpty()){store.terminal(s,"rejected","invalid_target");return;}
            var item=items.getFirst();if((int)item.get("answer_revision")!=input.inputRevision){store.terminal(s,"rejected","input_revision_conflict");return;}
            if(input.retryOfId!=null){var previous=store.db().queryForList("SELECT id FROM grading_submissions WHERE id=? AND user_id=? AND attempt_item_id=? AND status IN("+RETRYABLE_STATUSES+")",input.retryOfId,user,input.attemptItemId);if(previous.isEmpty()){store.terminal(s,"rejected","invalid_retry");return;}}
            if(store.db().queryForObject("SELECT count(*) FROM grading_submissions WHERE attempt_item_id=? AND status IN('draft','queued','running','graded')",Integer.class,input.attemptItemId)>0){store.terminal(s,"rejected","active_or_graded_submission");return;}
            var task=catalog.snapshot((UUID)item.get("task_version_id"));if(task.taskNumber()<14){store.terminal(s,"rejected","short_answer_requires_check");return;}
            store.db().update("UPDATE grading_submissions SET attempt_id=?,attempt_item_id=?,task_version_id=?,answer_snapshot=?,is_demo=?,retry_of_id=? WHERE id=?",input.attemptId,input.attemptItemId,task.taskVersionId(),item.get("answer"),task.isDemo(),input.retryOfId,id);store.event(id,"target_validated",user,Map.of());
        });return store.get(user,id);
    }
    public JsonNode finalizeSubmission(UUID user,UUID id,int revision){var capabilities=gateway.capabilities();ApiException error=tx.execute(t->{ApiException errorResult;try{errorResult=finalizeLocked(user,id,revision,capabilities);}catch(ApiException e){if(e.status()==404)throw e;errorResult=e;}if(errorResult!=null)store.event(id,"finalize_rejected",user,Map.of("code",errorResult.code()));return errorResult;});if(error!=null)throw error;return store.get(user,id);}
    private ApiException finalizeLocked(UUID user,UUID id,int revision,Map<Integer,GradingGateway.Capability> capabilities){Row s=store.row(id,user,true);if(!s.status().equals("draft"))return null;store.revision(s,revision);
        if(s.version()==null)return ApiException.conflict("invalid_target");
        if(store.db().queryForObject("SELECT answer_revision FROM attempt_items WHERE id=?",Integer.class,s.item())!=s.inputRevision())return ApiException.conflict("input_revision_conflict");
        int staged=store.db().queryForObject("SELECT count(*) FROM stored_objects WHERE submission_id=? AND state='staged'",Integer.class,id);if(staged>0)return ApiException.conflict("uploads_in_flight");
        int ready=store.db().queryForObject("SELECT count(*) FROM stored_objects WHERE submission_id=? AND state='ready'",Integer.class,id);if(ready<1)return new ApiException(422,"images_required","Добавь фотографию решения");
        var task=catalog.snapshot(s.version());var capability=capabilities.get(task.taskNumber());
        if(capability==null){store.event(id,"finalize_unavailable",user,Map.of());return new ApiException(503,"grading_unavailable","Проверка временно недоступна");}
        if(!capability.supported()||!catalog.aiInputReady(s.version())){store.terminal(s,"unsupported","unsupported_task");result(s,false,null,task.maxScore(),null,"Проверка этого задания пока недоступна",task.isDemo(),null,null,null);return null;}
        String plan=store.db().queryForObject("SELECT plan_id FROM users WHERE id=? AND enabled FOR UPDATE",String.class,user);
        if(!"pro".equals(plan)){store.event(id,"entitlement_denied",user,Map.of());return new ApiException(403,"ai_review_required","Проверка фото доступна на тарифе Pro");}
        // Global advisory lock makes capacity admission atomic across users without holding it during HTTP.
        store.db().execute("SELECT pg_advisory_xact_lock(726118013)");
        if(store.db().queryForObject("SELECT count(*) FROM grading_jobs WHERE state<>'done'",Integer.class)>=queueLimit)return new ApiException(429,"queue_full","Очередь заполнена. Повтори позже");
        // Immutable admission events charge the actual enqueue time, including later failures/cancellations.
        if(store.db().queryForObject("SELECT count(*) FROM grading_submissions s WHERE s.user_id=? AND EXISTS(SELECT 1 FROM submission_events e WHERE e.submission_id=s.id AND e.event_type='queued' AND e.created_at>clock_timestamp()-interval '24 hours')",Integer.class,user)>=dailyLimit)return new ApiException(429,"daily_grading_limit","Дневной лимит проверок исчерпан");
        store.db().update("UPDATE grading_submissions SET status='queued',revision=revision+1,package_id=?,provider_mode=?,updated_at=now() WHERE id=?",capability.packageId(),capability.providerMode(),id);store.db().update("INSERT INTO grading_jobs(submission_id,state) VALUES(?,'ready')",id);store.db().update("INSERT INTO submission_events(submission_id,event_type,actor_id,metadata,created_at) VALUES(?,'queued',?,'{}'::jsonb,clock_timestamp())",id,user);store.touchAttempt(s.attempt());return null;
    }
    public JsonNode report(UUID user,UUID id,ClientRejection request){if(!Set.of("file_type","file_size","image_count","image_dimensions","decode_failed").contains(request.reason))throw ApiException.invalid("Некорректная причина");tx.executeWithoutResult(t->{var s=store.row(id,user,true);store.draft(s);store.revision(s,request.expectedRevision);store.event(id,"client_rejection_reported",user,Map.of("reason",request.reason,"trusted",false));});return store.get(user,id);}
    public JsonNode cancel(UUID user,UUID id){tx.executeWithoutResult(t->{var s=store.row(id,user,true);if(!Set.of("draft","queued","running").contains(s.status()))return;store.terminal(s,"cancelled","cancelled");store.db().update("UPDATE grading_jobs SET state='done',fencing_token=fencing_token+1,lease_until=NULL,updated_at=now() WHERE submission_id=?",id);store.db().update("UPDATE stored_objects SET state='delete_pending' WHERE submission_id=? AND state='staged'",id);});return store.get(user,id);}
    public AttemptDtos.Attempt checks(UUID user,UUID attemptId,String key,Check request){key(key);var caps=gateway.capabilities();String hash=store.json().hash(Map.of("attemptId",attemptId,"request",request));
        tx.executeWithoutResult(t->{var owned=store.db().queryForList("SELECT revision FROM attempts WHERE id=? AND user_id=? AND deleted_at IS NULL FOR UPDATE",attemptId,user);if(owned.isEmpty())throw ApiException.notFound();var old=store.db().queryForList("SELECT request_hash FROM attempt_checks WHERE user_id=? AND idempotency_key=?",user,key);if(!old.isEmpty()){if(!hash.equals(old.getFirst().get("request_hash")))throw ApiException.conflict("idempotency_conflict");return;}if((int)owned.getFirst().get("revision")!=request.revision)throw ApiException.conflict("revision_conflict");
            var items=store.db().queryForList("SELECT * FROM attempt_items WHERE attempt_id=? ORDER BY ordinal",attemptId);Set<UUID> selected=request.itemIds==null?null:new HashSet<>(request.itemIds);if(selected!=null&&(selected.size()!=request.itemIds.size()||!items.stream().map(i->(UUID)i.get("id")).toList().containsAll(selected)))throw ApiException.invalid("Некорректный список заданий");
            for(var item:items){UUID itemId=(UUID)item.get("id");if(selected!=null&&!selected.contains(itemId))continue;var task=catalog.snapshot((UUID)item.get("task_version_id"));var existing=store.db().queryForList("SELECT id,revision,status FROM grading_submissions WHERE attempt_item_id=? ORDER BY created_at DESC LIMIT 1",itemId);
                if(task.taskNumber()>=14){if(!existing.isEmpty()&&"draft".equals(existing.getFirst().get("status"))){var e=existing.getFirst();ApiException error=finalizeLocked(user,(UUID)e.get("id"),(int)e.get("revision"),caps);if(error!=null)store.event((UUID)e.get("id"),"batch_check_skipped",user,Map.of("code",error.code()));}continue;}
                String answer=(String)item.get("answer");if(answer.isBlank()||(!existing.isEmpty()&&"graded".equals(existing.getFirst().get("status"))))continue;
                UUID id=UUID.randomUUID();store.db().update("INSERT INTO grading_submissions(id,user_id,attempt_id,attempt_item_id,requested_attempt_id,requested_item_id,input_revision,idempotency_key,request_hash,task_version_id,answer_snapshot,is_demo,request_id,status,finished_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'graded',now())",id,user,attemptId,itemId,attemptId,itemId,item.get("answer_revision"),"short:"+id,hash,task.taskVersionId(),answer,task.isDemo(),UUID.fromString(RequestIds.current()));
                int score=task.acceptedAnswers().stream().map(SubmissionService::normalize).anyMatch(normalize(answer)::equals)?task.maxScore():0;var s=store.row(id,user,false);result(s,true,score,task.maxScore(),null,null,task.isDemo(),null,null,null);store.event(id,"short_answer_graded",user,Map.of("score",score));profile.acceptGraded(user,task.id(),id,score,task.maxScore(),task.isDemo());
            }store.db().update("INSERT INTO attempt_checks(user_id,idempotency_key,attempt_id,request_hash) VALUES(?,?,?,?)",user,key,attemptId,hash);store.touchAttempt(attemptId);
        });return attempts.get(user,attemptId);
    }
    static String normalize(String answer){return answer.strip().replace(',','.');}
    public void result(Row s,boolean graded,Integer score,int max,Object feedback,String reason,boolean demo,String exact,String mode,String pkg){store.db().update("INSERT INTO grading_results(submission_id,is_graded,score,max_score,feedback,rejection_reason,is_demo,exact_response,response_hash,provider_mode,package_id) VALUES(?,?,?,?,?::jsonb,?,?,?,?,?,?)",s.id(),graded,score,max,feedback==null?null:store.json().write(feedback),reason,demo,exact,exact==null?null:JsonCodec.sha256(exact),mode,pkg);}
}
