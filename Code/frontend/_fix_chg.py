path='src/views/change/ChangeImpactAnalysis.vue'
with open(path,'r',encoding='utf-8') as f:
 lines = f.readlines()

start = -1
for idx, ln in enumerate(lines):
 if 'label="紧急程度"' in ln:
 start = idx
 break
end = start
while end < len(lines):
 end +=1
 if '</el-descriptions-item>' in lines[end-1]:
 break

new_block = ''' <!-- P3-4: urgency紧急度（与 priority并列；3档 NORMAL/URGENT/CRITICAL） -->
 <el-descriptions-item label="变更紧迫度">
 <el-tag :type="getUrgencyTag(changeInfo.urgency)" size="small">
 {{ getUrgencyLabel(changeInfo.urgency) }}
 </el-tag>
 </el-descriptions-item>
 <el-descriptions-item label="优先级">
 <el-tag :type="getPriorityTag(changeInfo.priority)" size="small">
 {{ changeInfo.priority || '-' }}
 </el-tag>
 </el-descriptions-item>
'''
new_lines = lines[:start] + [new_block] + lines[end:]
text = ''.join(new_lines)

marker = 'const getPriorityTag = (priority: string) => {'
insertion = '''// P3-4: urgency紧急度3档枚举（NORMAL=普通5d / URGENT=紧急2d / CRITICAL=关键立即）
const URGENCY_TAG = { NORMAL: 'info', URGENT: 'warning', CRITICAL: 'danger' }
const URGENCY_LABEL = { NORMAL: '普通(5d)', URGENT: '紧急(2d)', CRITICAL: '关键(立即)' }
const getUrgencyTag = (u) => URGENCY_TAG[u || ''] || 'info'
const getUrgencyLabel = (u) => URGENCY_LABEL[u || ''] || (u || '-')

'''
text2 = text.replace(marker, insertion + marker,1)
with open(path,'w',encoding='utf-8') as f:
 f.write(text2)
print('done')
