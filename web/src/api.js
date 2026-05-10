const BIZ = "http://localhost:8080/api/v1/business";

export const USER_SESSION_STORAGE_KEY = "mienmien.user.session.v1";

function readSessionToken() {
  try {
    const raw = localStorage.getItem(USER_SESSION_STORAGE_KEY);
    if (!raw) return null;
    const p = JSON.parse(raw);
    return p?.sessionToken || null;
  } catch {
    return null;
  }
}

async function fetchJson(url, options = {}) {
  const skipAuth =
    url.includes("/auth/register") || url.includes("/auth/login");
  const headers = new Headers(options.headers || {});
  if (options.body != null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!skipAuth) {
    const token = readSessionToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }
  let res;
  try {
    res = await fetch(url, { ...options, headers });
  } catch (e) {
    const msg = typeof e?.message === "string" ? e.message : "";
    const looksNetwork =
      e instanceof TypeError || msg.includes("Failed to fetch") || msg.includes("NetworkError") || msg.includes("Load failed");
    if (looksNetwork) {
      throw new Error(
        "无法连接业务服务。请在本机启动 business（默认端口 8080），例如执行仓库根目录下的 `bash scripts/dev-all-jdk21.sh`，或单独启动 `java/business` 后再试。"
      );
    }
    throw e;
  }
  const text = await res.text();
  let data = {};
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = {};
    }
  }
  if (!res.ok) {
    if (res.status === 401 && data?.code === "BUS-4010") {
      try {
        localStorage.removeItem(USER_SESSION_STORAGE_KEY);
      } catch {
        /* ignore */
      }
    }
    throw new Error(data.message || `HTTP ${res.status}`);
  }
  return data;
}

export async function createSpace(payload) {
  return fetchJson(`${BIZ}/spaces`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function listSpaces() {
  return fetchJson(`${BIZ}/spaces`);
}

export async function listRecycleBinSpaces() {
  return fetchJson(`${BIZ}/spaces/recycle-bin`);
}

export async function renameSpace(spaceId, payload) {
  return fetchJson(`${BIZ}/spaces/${spaceId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function archiveSpace(spaceId) {
  return fetchJson(`${BIZ}/spaces/${spaceId}`, { method: "DELETE" });
}

export async function deleteSpace(spaceId) {
  return fetchJson(`${BIZ}/spaces/${spaceId}/hard`, { method: "DELETE" });
}

export async function recycleSpace(spaceId) {
  return fetchJson(`${BIZ}/spaces/${spaceId}`, { method: "DELETE" });
}

export async function restoreSpace(spaceId) {
  return fetchJson(`${BIZ}/spaces/${spaceId}/restore`, { method: "POST" });
}

export async function listResumeDocuments(spaceId) {
  return fetchJson(`${BIZ}/spaces/${encodeURIComponent(spaceId)}/resume-documents`);
}

export async function getResumeDocument(spaceId, resumeId) {
  return fetchJson(
    `${BIZ}/spaces/${encodeURIComponent(spaceId)}/resume-documents/${encodeURIComponent(resumeId)}`
  );
}

export async function createResumeDocument(spaceId, payload) {
  return fetchJson(`${BIZ}/spaces/${encodeURIComponent(spaceId)}/resume-documents`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function updateResumeDocument(spaceId, resumeId, payload) {
  return fetchJson(
    `${BIZ}/spaces/${encodeURIComponent(spaceId)}/resume-documents/${encodeURIComponent(resumeId)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }
  );
}

export async function deleteResumeDocument(spaceId, resumeId) {
  return fetchJson(
    `${BIZ}/spaces/${encodeURIComponent(spaceId)}/resume-documents/${encodeURIComponent(resumeId)}`,
    { method: "DELETE" }
  );
}

/** 当前用户全部简历（每份一条，含 spaceIds） */
export async function listAllResumeDocuments() {
  return fetchJson(`${BIZ}/resume-documents`);
}

/** 创建简历；body 含 name、modules，可选 spaceId（有当前工作空间时带上即可自动关联） */
export async function createResumeDocumentMine(payload) {
  return fetchJson(`${BIZ}/resume-documents`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function getResumeDocumentById(resumeId) {
  return fetchJson(`${BIZ}/resume-documents/${encodeURIComponent(resumeId)}`);
}

/** 更新简历正文（不依赖空间路径；适用于尚未关联任何空间的简历） */
export async function updateResumeDocumentById(resumeId, payload) {
  return fetchJson(`${BIZ}/resume-documents/${encodeURIComponent(resumeId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

/** 将已有简历关联到目标空间（幂等） */
export async function linkResumeToSpace(spaceId, resumeId) {
  return fetchJson(
    `${BIZ}/spaces/${encodeURIComponent(spaceId)}/resume-documents/${encodeURIComponent(resumeId)}/link`,
    { method: "POST" }
  );
}

/** 删除简历及其全部空间关联 */
export async function deleteResumeDocumentEntire(resumeId) {
  return fetchJson(`${BIZ}/resume-documents/${encodeURIComponent(resumeId)}`, { method: "DELETE" });
}

export async function createJd(payload) {
  return fetchJson(`${BIZ}/jd-targets`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function listJd(spaceId) {
  return fetchJson(`${BIZ}/jd-targets/${spaceId}`);
}

export async function analyzeJdFocusPoints(rawText) {
  return fetchJson(`${BIZ}/jd-targets/analyze`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ rawText })
  });
}

export async function createInterview(type, payload) {
  return fetchJson(`${BIZ}/interviews/${type}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function listInterview(spaceId) {
  return fetchJson(`${BIZ}/interviews/${spaceId}`);
}

export async function createJobPosition(payload) {
  return fetchJson(`${BIZ}/job-positions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function listJobPositions(spaceId) {
  return fetchJson(`${BIZ}/job-positions/by-space/${encodeURIComponent(spaceId)}`);
}

export async function listAllJobPositions() {
  return fetchJson(`${BIZ}/job-positions`);
}

export async function getJobPositionById(positionId) {
  return fetchJson(`${BIZ}/job-positions/item/${encodeURIComponent(positionId)}`);
}

export async function linkJobToSpace(spaceId, positionId) {
  return fetchJson(
    `${BIZ}/spaces/${encodeURIComponent(spaceId)}/job-positions/${encodeURIComponent(positionId)}/link`,
    { method: "POST" }
  );
}

/** 从指定空间解除岗位关联（不删除岗位主体） */
export async function unlinkJobFromSpace(spaceId, positionId) {
  return fetchJson(
    `${BIZ}/spaces/${encodeURIComponent(spaceId)}/job-positions/${encodeURIComponent(positionId)}/link`,
    { method: "DELETE" }
  );
}

export async function updateJobPosition(positionId, payload) {
  return fetchJson(`${BIZ}/job-positions/item/${positionId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function closeJobPosition(positionId) {
  return fetchJson(`${BIZ}/job-positions/item/${positionId}`, { method: "DELETE" });
}

export async function getAnswerBank(spaceId) {
  return fetchJson(`${BIZ}/answer-banks/${spaceId}`);
}

export async function saveAnswerBank(payload) {
  return fetchJson(`${BIZ}/answer-banks`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function getModelConfig() {
  return fetchJson(`${BIZ}/model-configs/me`);
}

export async function saveModelConfig(payload) {
  return fetchJson(`${BIZ}/model-configs`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

/** 服务端读取库中 API Key / Base URL，按传入提示词与模型名调用大模型（测试调用）。 */
export async function testModelConfig(payload) {
  return fetchJson(`${BIZ}/model-configs/test`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

/** 库表看板：当前库所有表名（需后端允许的手机号登录）。 */
export async function listDbInspectorTables() {
  return fetchJson(`${BIZ}/admin/db-inspector/tables`);
}

/** 库表看板：分页读取指定表数据。 */
export async function listDbInspectorTableRows(tableName, offset = 0, limit = 100) {
  const q = new URLSearchParams({ offset: String(offset), limit: String(limit) });
  return fetchJson(
    `${BIZ}/admin/db-inspector/tables/${encodeURIComponent(tableName)}/rows?${q.toString()}`
  );
}

export async function registerByPhone(payload) {
  return fetchJson(`${BIZ}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function loginByPhone(payload) {
  return fetchJson(`${BIZ}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

/** 注销服务端会话（需本地已有 sessionToken） */
export async function logoutSession() {
  const token = readSessionToken();
  if (!token) return;
  const res = await fetch(`${BIZ}/auth/logout`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` }
  });
  if (res.status === 401) {
    try {
      localStorage.removeItem(USER_SESSION_STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }
}
