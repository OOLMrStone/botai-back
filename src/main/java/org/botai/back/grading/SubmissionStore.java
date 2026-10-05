package org.botai.back.grading;

import lombok.RequiredArgsConstructor;
import org.botai.back.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class SubmissionStore {
    private final JdbcTemplate db;private final JsonCodec json;
    public JdbcTemplate db(){return db;} public JsonCodec json(){return json;}
    public record Row(UUID id,UUID user,UUID attempt,UUID item,UUID requestedAttempt,UUID requestedItem,UUID version,int inputRevision,int revision,String status,String answer,String packageId,String mode,boolean demo) { }
    public Row row(UUID id,UUID user,boolean lock){if(lock){var owners=db.queryForList("SELECT coalesce(attempt_id,requested_attempt_id) aid,user_id FROM grading_submissions WHERE id=?",id);if(!owners.isEmpty()){var o=owners.getFirst();db.queryForList("SELECT id FROM attempts WHERE id=? AND user_id=? FOR UPDATE",o.get("aid"),user==null?o.get("user_id"):user);}}var rows=db.query("SELECT * FROM grading_submissions WHERE id=?"+(user==null?"":" AND user_id=?")+(lock?" FOR UPDATE":""),(r,n)->new Row(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getObject("attempt_id",UUID.class),r.getObject("attempt_item_id",UUID.class),r.getObject("requested_attempt_id",UUID.class),r.getObject("requested_item_id",UUID.class),r.getObject("task_version_id",UUID.class),r.getInt("input_revision"),r.getInt("revision"),r.getString("status"),r.getString("answer_snapshot"),r.getString("package_id"),r.getString("provider_mode"),r.getBoolean("is_demo")),user==null?new Object[]{id}:new Object[]{id,user});if(rows.isEmpty())throw ApiException.notFound();return rows.getFirst();}
    public void event(UUID id,String type,UUID actor,Object metadata){db.update("INSERT INTO submission_events(submission_id,event_type,actor_id,metadata) VALUES (?,?,?,?::jsonb)",id,type,actor,json.write(metadata));}
    public void draft(Row row){if(!row.status.equals("draft"))throw ApiException.conflict("submission_not_draft");}
    public void revision(Row row,int expected){if(row.revision!=expected)throw ApiException.conflict("revision_conflict");}
    public JsonNode get(UUID user,UUID id){row(id,user,false);return json.read(db.queryForObject("""
        SELECT jsonb_build_object('id',s.id,'attemptId',coalesce(s.attempt_id,s.requested_attempt_id),'attemptItemId',coalesce(s.attempt_item_id,s.requested_item_id),'inputRevision',s.input_revision,'revision',s.revision,'status',s.status,
         'images',coalesce((SELECT jsonb_agg(jsonb_build_object('id',o.id,'fileName',o.file_name,'mimeType',o.mime_type,'sizeBytes',o.size_bytes,'status',o.state,'previewUrl','/api/media/'||o.id) ORDER BY o.ordinal) FROM stored_objects o WHERE o.submission_id=s.id AND o.state='ready'),'[]'::jsonb),
         'createdAt',s.created_at,'updatedAt',s.updated_at,'errorCode',s.error_code,'retryable',s.status IN('failed','rejected','cancelled','unsupported'),'isDemo',s.is_demo,
         'result',CASE WHEN r.submission_id IS NULL THEN NULL ELSE jsonb_build_object('isGraded',r.is_graded,'score',r.score,'maxScore',r.max_score,'feedback',r.feedback,'rejectionReason',r.rejection_reason,'isDemo',r.is_demo) END)::text
        FROM grading_submissions s LEFT JOIN grading_results r ON r.submission_id=s.id WHERE s.id=?
        """,String.class,id));}
    public void terminal(Row s,String status,String code){db.update("UPDATE grading_submissions SET status=?,error_code=?,revision=revision+1,updated_at=now(),finished_at=now() WHERE id=?",status,code,s.id);event(s.id,status,null,Map.of("code",code));touchAttempt(s.attempt);}
    public void touchAttempt(UUID attempt){if(attempt==null)return;db.update("""
        UPDATE attempts SET revision=revision+1,updated_at=now(),status=CASE
         WHEN EXISTS(SELECT 1 FROM grading_submissions WHERE attempt_id=? AND status IN('queued','running')) THEN 'checking'
         WHEN NOT EXISTS(SELECT 1 FROM attempt_items i WHERE i.attempt_id=? AND NOT EXISTS(SELECT 1 FROM grading_submissions s JOIN grading_results r ON r.submission_id=s.id WHERE s.attempt_item_id=i.id AND s.input_revision=i.answer_revision AND s.status='graded' AND r.is_graded)) THEN 'completed' ELSE 'active' END,
         completed_at=CASE WHEN NOT EXISTS(SELECT 1 FROM attempt_items i WHERE i.attempt_id=? AND NOT EXISTS(SELECT 1 FROM grading_submissions s JOIN grading_results r ON r.submission_id=s.id WHERE s.attempt_item_id=i.id AND s.input_revision=i.answer_revision AND s.status='graded' AND r.is_graded)) THEN now() ELSE NULL END WHERE id=?
        """,attempt,attempt,attempt,attempt);}
}
