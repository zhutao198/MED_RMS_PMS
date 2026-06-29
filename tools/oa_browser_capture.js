/**
 * R101 浏览器抓取脚本 v1.0
 * 适用：泛微 e-cology v8 OA(已登录浏览器)
 * 目的：从 OA SPA DOM/网络抓取数据 → POST 到 Med-RMS /api/oa-sync/*
 *
 * 使用方法：
 *   1. 浏览器登录 OA(已登录)
 *   2. 导航到要抓取的页面(通讯录/组织架构/流程列表)
 *   3. F12 打开 Console,粘贴本脚本
 *   4. 回车执行,看输出
 *
 * 工作原理：
 *   - SPA 把数据渲染到 DOM,直接从 DOM 抓取
 *   - 抓不到时,自动调用 SPA 内部 fetch API
 *   - 数据 POST 到 Med-RMS 接收端点(需后端服务在跑)
 */

// ===== 配置 =====
const MED_RMS_BASE = 'http://localhost:8080/api';  // Med-RMS 后端地址
// 如需 JWT,粘贴你的 token 字符串(可从 DevTools Application/Storage 看)
// const MED_RMS_TOKEN = localStorage.getItem('token') || '';

const headers = {
  'Content-Type': 'application/json;charset=UTF-8',
  // 'Authorization': 'Bearer ' + MED_RMS_TOKEN,  // 如后端需要 JWT
};

// ===== 通用工具 =====
async function postToMedRms(endpoint, data) {
  const url = `${MED_RMS_BASE}/oa-sync/${endpoint}`;
  console.log(`📤 POST ${url} (${Array.isArray(data) ? data.length : 1} 条)`);
  try {
    const r = await fetch(url, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(data),
      credentials: 'include'
    });
    const text = await r.text();
    let json;
    try { json = JSON.parse(text); } catch { json = { raw: text }; }
    console.log(`  ✅ HTTP=${r.status}`, JSON.stringify(json).slice(0, 300));
    return json;
  } catch (e) {
    console.error(`  ❌ 网络错误:`, e);
    return null;
  }
}

// ===== 1. 抓取组织架构(分部+部门) =====
async function captureOrgStructure() {
  console.log('\n📋 1. 抓取组织架构...');
  // 方案 A: 从 OA SPA 的 React 内部 store 抓
  let orgData = [];
  try {
    // e-cology SPA 用 mobx,store 在 window.__MOBX_STATE__ 或 window.__INITIAL_STATE__
    if (window.__MOBX_STATE__) {
      orgData = extractFromMobx(window.__MOBX_STATE__);
    }
  } catch (e) {}

  // 方案 B: 从 DOM 抓(左侧菜单/树)
  if (orgData.length === 0) {
    orgData = extractOrgFromDOM();
  }

  if (orgData.length === 0) {
    console.log('  ⚠️  DOM 无数据。请先在左侧菜单点击"分部/部门"展开列表');
    return null;
  }
  console.log(`  ✅ 抓到 ${orgData.length} 个节点`);
  return orgData;
}

function extractOrgFromDOM() {
  // 泛微 e-cology 通讯录/分部页 DOM 结构:
  // <div class="ant-tree-node-content-wrapper">
  //   <span class="ant-tree-title">部门名</span>
  const treeNodes = document.querySelectorAll('.ant-tree-title, .el-tree-node__label');
  return Array.from(treeNodes).map(el => ({
    _fullname: el.textContent.trim(),
    _code: '',
    _showorder: '0'
  })).filter(n => n._fullname);
}

function extractFromMobx(state) {
  // 简化:遍历 mobx state 找数组形态的数据
  const results = [];
  function walk(obj, depth = 0) {
    if (depth > 5) return;
    if (!obj || typeof obj !== 'object') return;
    if (Array.isArray(obj)) {
      obj.forEach(item => {
        if (item && typeof item === 'object' && (item._fullname || item.departmentname)) {
          results.push(item);
        }
      });
    }
    Object.values(obj).forEach(v => walk(v, depth + 1));
  }
  walk(state);
  return results;
}

// ===== 2. 抓取人员(从通讯录页 DOM) =====
async function captureUsers() {
  console.log('\n👥 2. 抓取人员...');
  // 泛微 e-cology 通讯录页通常用 ant-table 渲染
  const rows = document.querySelectorAll('.ant-table-row, .ant-table-tbody > tr');
  if (rows.length === 0) {
    console.log('  ⚠️  未找到人员表格行。请先导航到"通讯录"页面');
    return null;
  }

  const users = [];
  rows.forEach(row => {
    const cells = row.querySelectorAll('td');
    if (cells.length < 3) return;
    // 泛微通讯录列顺序(常见):登录名/姓名/部门/职位/手机/邮箱
    users.push({
      workcode: cells[0]?.textContent.trim() || '',
      lastname: cells[1]?.textContent.trim() || '',
      departmentname: cells[2]?.textContent.trim() || '',
      jobtitle: cells[3]?.textContent.trim() || '',
      mobile: cells[4]?.textContent.trim() || '',
      email: cells[5]?.textContent.trim() || ''
    });
  });
  console.log(`  ✅ 抓到 ${users.length} 个用户`);
  return users;
}

// ===== 3. 抓取工作流(紧急需求单 workflowid=535 / 需求采集卡 workflowid=554) =====
async function captureWorkflow(workflowid, label) {
  console.log(`\n📝 3. 抓取工作流 [${label}] workflowid=${workflowid}...`);

  // 方案 A: 调泛微 SPA 内部 API getAllWorkflowRequestList
  // 通过本地观察: SPA 用 /api/workflow/wo/getAllWorkflowRequestList?workflowid=N
  const apiUrl = `/api/workflow/wo/getAllWorkflowRequestList?workflowid=${workflowid}&pageNo=1&pageSize=200`;
  try {
    const r = await fetch(apiUrl, { credentials: 'include' });
    if (r.ok) {
      const data = await r.json();
      console.log(`  ✅ API 响应 status=${r.status}`);
      // 提取工作流列表(具体字段名取决于 OA 返回)
      const list = data.data?.datas || data.data?.records || (Array.isArray(data.data) ? data.data : []);
      console.log(`  抓到 ${list.length} 条`);
      return list;
    } else {
      console.log(`  ⚠️  API 返回 ${r.status}`);
    }
  } catch (e) {
    console.log(`  ⚠️  API 调用失败:`, e.message);
  }

  // 方案 B: 从 DOM 抓(如果已在工作流列表页)
  const rows = document.querySelectorAll('.ant-table-row');
  if (rows.length > 0) {
    const list = Array.from(rows).map(row => {
      const cells = row.querySelectorAll('td');
      return {
        requestid: cells[0]?.textContent.trim(),
        title: cells[1]?.textContent.trim(),
        status: cells[2]?.textContent.trim(),
        creator: cells[3]?.textContent.trim(),
        createdate: cells[4]?.textContent.trim()
      };
    });
    console.log(`  ✅ DOM 抓到 ${list.length} 条`);
    return list;
  }

  console.log(`  ⚠️  两种方案都未抓到。请手动导航到"流程 → 我的请求 → ${label}"页面`);
  return null;
}

// ===== 4. 主流程 =====
async function main() {
  console.log('🚀 R101 OA → Med-RMS 同步脚本启动');
  console.log('═'.repeat(50));

  // 1. 同步组织架构
  const org = await captureOrgStructure();
  if (org) {
    // 区分 subcompany / department(简化:都当 subcompany 处理)
    await postToMedRms('subcompanies', org);
  }

  // 2. 同步人员
  const users = await captureUsers();
  if (users) {
    await postToMedRms('users', users);
  }

  // 3. 同步紧急需求单
  const urgent = await captureWorkflow(535, '紧急需求单');
  if (urgent) {
    await postToMedRms('urgent-requirements', urgent);
  }

  // 4. 同步需求采集卡
  const card = await captureWorkflow(554, '需求采集卡');
  if (card) {
    await postToMedRms('requirement-cards', card);
  }

  console.log('\n✅ 全部完成');
}

main();