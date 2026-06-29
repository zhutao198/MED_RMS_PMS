# -*- coding: utf-8 -*-
path = r'D:\zhutao\MED_RMS_PMS\Code\frontend\src\views\requirement\RequirementList.vue'
f = open(path, 'r', encoding='utf-8')
content = f.read()
f.close()

old = ' <el-table :data="filteredRequirements" style="width:100%; margin-top:20px;" v-loading="loading">'
new = ''' <!-- P2-1修复：行点击触发详情 drawer 不跳页 -->
 <el-table
 :data="filteredRequirements"
 style="width:100%; margin-top:20px;"
 v-loading="loading"
 row-class-name="req-row-clickable"
 @row-click="openDetailDrawer"
 >'''
print('OLD in content:', old in content)
if old not in content:
 print('NOT FOUND')
else:
 content = content.replace(old, new,1)
 f = open(path, 'w', encoding='utf-8')
 f.write(content)
 f.close()
 print('OK')
