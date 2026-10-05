"""Validate a small inert dvisvgm vector subset before offline rasterization."""
from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass

SVG = 'http://www.w3.org/2000/svg'
XLINK = 'http://www.w3.org/1999/xlink'
MAX_SOURCE_BYTES = 2 * 1024 * 1024
MAX_ELEMENTS = 20000
MAX_EXPANDED_COST = 200000
NUMBER = r'[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?'
NUMBER_RE = re.compile(NUMBER)
ATTRS = {
    'svg': {'version', 'width', 'height', 'viewBox'}, 'defs': set(),
    'g': {'id', 'transform', 'fill'},
    'path': {'id', 'd', 'fill', 'stroke', 'stroke-width', 'stroke-miterlimit'},
    'rect': {'x', 'y', 'width', 'height', 'fill'},
    'use': {'x', 'y', '{' + XLINK + '}href'},
}


@dataclass(frozen=True)
class ValidatedSvg:
    data: bytes
    width_css_px: float
    height_css_px: float
    expanded_cost: int


def numbers(value):
    tokens = NUMBER_RE.findall(value)
    if not tokens or NUMBER_RE.sub('', value).strip(' ,\t\r\n'):
        raise ValueError('Invalid SVG numeric grammar')
    result = [float(token) for token in tokens]
    if any(not math.isfinite(n) or abs(n) > 1000000 for n in result):
        raise ValueError('SVG coordinate budget')
    return result


def dimension(value):
    match = re.fullmatch('(' + NUMBER + r')(pt|px)?', value)
    if not match:
        raise ValueError('Only intrinsic pt/px SVG dimensions are supported')
    n = numbers(match.group(1))[0] * (4 / 3 if match.group(2) == 'pt' else 1)
    if not 0 < n <= 8192:
        raise ValueError('SVG dimension budget')
    return n


def path_cost(value):
    if len(value) > 512000:
        raise ValueError('SVG path byte budget')
    token_pattern = re.compile(NUMBER + r'|[MmLlHhVvCcSsQqTtAaZz]')
    tokens = token_pattern.findall(value)
    if not tokens or token_pattern.sub('', value).strip(' ,\t\r\n'):
        raise ValueError('Unsupported SVG path grammar')
    if tokens[0] not in {'M', 'm'}:
        raise ValueError('SVG path must start with move')
    arity = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'C': 6, 'S': 4, 'Q': 4, 'T': 2, 'A': 7, 'Z': 0}
    index, cost = 0, 1
    while index < len(tokens):
        command = tokens[index]
        if command.upper() not in arity:
            raise ValueError('Missing SVG path command')
        index += 1
        start = index
        while index < len(tokens) and tokens[index].upper() not in arity:
            index += 1
        numeric_tokens = tokens[start:index]
        values = [float(token) for token in numeric_tokens]
        count = arity[command.upper()]
        if (count == 0 and values) or (count and (not values or len(values) % count)):
            raise ValueError('Incomplete SVG path command')
        if any(not math.isfinite(n) or abs(n) > 1000000 for n in values):
            raise ValueError('SVG path coordinate budget')
        if command.upper() == 'A':
            for n in range(0, len(values), 7):
                if min(values[n:n+2]) < 0 or numeric_tokens[n+3] not in {'0', '1'} or numeric_tokens[n+4] not in {'0', '1'}:
                    raise ValueError('Invalid SVG arc')
        cost += len(values)
    return cost


def validate_svg(data: bytes) -> ValidatedSvg:
    if len(data) > MAX_SOURCE_BYTES:
        raise ValueError('SVG source byte budget')
    text = data.decode('utf-8', errors='strict')
    if any(ord(c) < 32 and c not in '\t\n\r' for c in text):
        raise ValueError('XML control character or encoded-source ambiguity')
    lowered = text.lower()
    if '<!doctype' in lowered or '<!entity' in lowered:
        raise ValueError('SVG entity declaration')
    without_declaration = re.sub(r'^\s*<\?xml\s+version=[\"\']1\.0[\"\'](?:\s+encoding=[\"\']UTF-8[\"\'])?\s*\?>', '', text, count=1, flags=re.IGNORECASE)
    if '<?' in without_declaration:
        raise ValueError('SVG processing instruction')
    root = ET.fromstring(text)
    if root.tag != '{' + SVG + '}svg':
        raise ValueError('SVG root namespace required')
    elements = list(root.iter())
    if len(elements) > MAX_ELEMENTS:
        raise ValueError('SVG element budget')
    ids, own_cost = {}, {}
    for node in elements:
        if not isinstance(node.tag, str) or not node.tag.startswith('{' + SVG + '}'):
            raise ValueError('Foreign SVG namespace')
        tag = node.tag[len(SVG) + 2:]
        if tag not in ATTRS or set(node.attrib) - ATTRS[tag]:
            raise ValueError('Unsupported SVG element or attribute')
        if (node.text or '').strip() or (node.tail or '').strip():
            raise ValueError('SVG text is outside the approved vector subset')
        if tag == 'svg' and node is not root:
            raise ValueError('Nested SVG not supported')
        identity = node.get('id')
        if identity:
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_-]{0,63}', identity) or identity in ids:
                raise ValueError('Invalid or duplicate SVG identity')
            ids[identity] = node
        cost = 1
        for key, value in node.attrib.items():
            if key != 'd' and len(value) > 4096:
                raise ValueError('SVG attribute byte budget')
            if key in {'width', 'height'} and tag != 'svg':
                if len(numbers(value)) != 1 or numbers(value)[0] < 0:
                    raise ValueError('Invalid SVG vector dimension')
                continue
            if key in {'id', 'version', 'width', 'height', 'viewBox', '{' + XLINK + '}href'}:
                continue
            if key in {'fill', 'stroke'}:
                if not re.fullmatch(r'none|#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?', value):
                    raise ValueError('SVG paint must be a literal color')
            elif key == 'd':
                cost += path_cost(value)
            elif key == 'transform':
                rest = value
                for match in re.finditer(r'(matrix|translate|scale|rotate)\(([^()]*)\)', value):
                    vals = numbers(match.group(2))
                    allowed = {'matrix': {6}, 'translate': {1, 2}, 'scale': {1, 2}, 'rotate': {1, 3}}
                    if len(vals) not in allowed[match.group(1)]:
                        raise ValueError('Invalid SVG transform')
                    rest = rest.replace(match.group(0), '', 1)
                if rest.strip():
                    raise ValueError('Unsupported SVG transform')
            else:
                if len(numbers(value)) != 1:
                    raise ValueError('Invalid SVG scalar')
        own_cost[id(node)] = cost
    width, height = dimension(root.get('width', '')), dimension(root.get('height', ''))
    viewbox = numbers(root.get('viewBox', ''))
    if len(viewbox) != 4 or viewbox[2] <= 0 or viewbox[3] <= 0 or width * height > 20000000:
        raise ValueError('Invalid SVG viewBox or pixel budget')
    memo = {}

    def expanded(node, active):
        key = id(node)
        if key in active or len(active) >= 32:
            raise ValueError('Cyclic or deep SVG local reference')
        if key in memo:
            cost, depth = memo[key]
            if len(active) + depth > 32:
                raise ValueError('Deep SVG local reference')
            return cost, depth
        cost, depth = own_cost[key], 1
        for child in node:
            child_cost, child_depth = expanded(child, active | {key})
            cost += child_cost
            depth = max(depth, 1 + child_depth)
        if node.tag == '{' + SVG + '}use':
            href = node.get('{' + XLINK + '}href', '')
            target = ids.get(href[1:]) if href.startswith('#') else None
            if target is None or target.tag not in {'{' + SVG + '}path', '{' + SVG + '}g'}:
                raise ValueError('SVG use must reference a local path/group')
            child_cost, child_depth = expanded(target, active | {key})
            cost += child_cost
            depth = max(depth, 1 + child_depth)
        if cost > MAX_EXPANDED_COST:
            raise ValueError('SVG expanded path budget')
        if len(active) + depth > 32:
            raise ValueError('Deep SVG local reference')
        memo[key] = (cost, depth)
        return cost, depth

    cost, _ = expanded(root, set())
    ET.register_namespace('', SVG)
    ET.register_namespace('xlink', XLINK)
    return ValidatedSvg(ET.tostring(root, encoding='utf-8', xml_declaration=True), width, height, cost)
