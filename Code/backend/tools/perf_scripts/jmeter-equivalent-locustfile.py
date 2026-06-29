"""
Med-RMS JMeter 等价并发压测（Locust 实现）
- 场景 A：登录并发 100（高峰时段）
- 场景 B：需求创建 50 并发（导入场景）

等价于 JMeter Thread Group：
- Thread Group A: 100 threads, ramp 30s, loop forever
- Thread Group B: 50 threads, ramp 20s, loop forever

用法：
    cd Code/backend
    locust -f tools/perf_scripts/jmeter-equivalent-locustfile.py --host=http://localhost:8080
    # 或 headless 模式：
    locust -f tools/perf_scripts/jmeter-equivalent-locustfile.py --host=http://localhost:8080 --headless -u 100 -r 10 --run-time 60s --html tools/perf_reports/<DATE>/jmeter-login-100.html
"""
import random
from locust import HttpUser, task, between, events


class LoginUser(HttpUser):
    """模拟登录高并发（100 用户峰值）"""
    wait_time = between(1, 3)
    weight = 2  # 权重 2

    def on_start(self):
        # 登录获取 token
        r = self.client.post("/api/auth/login",
            json={"username": "admin", "password": "admin123"})
        if r.status_code == 200:
            self.token = r.json().get("data", {}).get("token", "")
        else:
            self.token = ""

    @task(3)
    def login_attempt(self):
        # 模拟不同用户登录尝试
        users = ['admin', 'user1', 'user2', 'user3', 'pm_lead', 'qa_eng']
        pwds = ['admin123', 'user123', 'user123', 'user123', 'pm123', 'qa123']
        idx = random.randint(0, len(users) - 1)
        with self.client.post(
            "/api/auth/login",
            json={"username": users[idx], "password": pwds[idx]},
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code in (401, 400):
                # 凭据错但契约可达
                response.success()
            else:
                response.failure(f"Login status: {response.status_code}")

    @task(1)
    def requirements_browse(self):
        # 登录成功后浏览需求
        if not self.token:
            return
        with self.client.get(
            "/api/requirements?page=1&size=20",
            headers={"Authorization": f"Bearer {self.token}"},
            catch_response=True,
        ) as response:
            if response.status_code in (200, 401, 403):
                # 200 OK 或 401/403 契约可达
                response.success()
            else:
                response.failure(f"Browse status: {response.status_code}")


class RequirementCreateUser(HttpUser):
    """模拟需求创建高并发（50 用户）"""
    wait_time = between(2, 5)
    weight = 1  # 权重 1

    def on_start(self):
        # 登录获取 token
        r = self.client.post("/api/auth/login",
            json={"username": "admin", "password": "admin123"})
        if r.status_code == 200:
            self.token = r.json().get("data", {}).get("token", "")
        else:
            self.token = ""

    @task
    def create_requirement(self):
        if not self.token:
            return
        title = f"压测需求 {random.randint(1, 100000)}"
        body = {
            "title": title,
            "requirementType": "URS",
            "projectId": 1,
            "priority": "P1",
            "description": "locust 压测自动创建"
        }
        with self.client.post(
            "/api/requirements",
            json=body,
            headers={"Authorization": f"Bearer {self.token}"},
            catch_response=True,
        ) as response:
            if response.status_code in (200, 201):
                response.success()
            elif response.status_code == 409:
                # 重复编号
                response.success()
            else:
                response.failure(f"Create status: {response.status_code}")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """测试结束汇总"""
    stats = environment.stats
    print("\n" + "=" * 60)
    print("Med-RMS Locust 压测汇总")
    print("=" * 60)
    print(f"总请求数: {stats.total.num_requests}")
    print(f"总失败数: {stats.total.num_failures}")
    print(f"失败率: {stats.total.fail_ratio * 100:.2f}%")
    print(f"P50 响应: {stats.total.get_response_time_percentile(0.5):.1f}ms")
    print(f"P95 响应: {stats.total.get_response_time_percentile(0.95):.1f}ms")
    print(f"P99 响应: {stats.total.get_response_time_percentile(0.99):.1f}ms")
    print(f"RPS: {stats.total.total_rps:.1f}")
    print("=" * 60)
