#!/usr/bin/env python3
"""Export UI input skeletons from generated OpenAPI and scene participant declarations.
Run after sppcompile. These are form defaults, never business calculations.
Requires PyYAML.
"""
from pathlib import Path
import json, re, yaml
root = Path(__file__).resolve().parents[1]
schemas = yaml.safe_load((root/'target/spp/src/main/resources/cboddeposit.spp.yaml').read_text())['components']['schemas']
def defaults(schema):
    if '$ref' in schema:
        target = schemas[schema['$ref'].split('/')[-1]]
        return target['enum'][0] if 'enum' in target else None
    if 'enum' in schema: return schema['enum'][0]
    return {'string':'','number':0,'integer':0,'boolean':False,'array':[]}.get(schema.get('type'))
result={}
for file in (root/'src/main/spp').rglob('*.spp'):
    for name,args in re.findall(r'Scenario\s+\w+\s+(\w+)\((.*?)\)',file.read_text(),re.S):
        fields={}
        for arg in args.split(','):
            parts=arg.strip().split()
            kind=parts[0]; alias=parts[-1] if len(parts)>1 else kind.replace('[]','Array')
            fields[alias]=[] if kind.endswith('[]') else {k:defaults(v) for k,v in schemas[kind].get('properties',{}).items() if defaults(v) is not None}
        for participant in fields.values():
            if isinstance(participant, dict) and 'currency' in participant:
                participant['currency'] = 'USD'
        result[name]=fields
(root/'src/main/resources/web/templates.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
