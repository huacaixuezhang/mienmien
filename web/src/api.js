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

export async function createResume(payload) {
  return fetchJson(`${BIZ}/resumes`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

export async function listResumes(spaceId) {
  return fetchJson(`${BIZ}/resumes/${spaceId}`);
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
  return fetchJson(`${BIZ}/job-positions/${spaceId}`);
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

export async function getModelConfig(spaceId) {
  return fetchJson(`${BIZ}/model-configs/${spaceId}`);
}

export async function saveModelConfig(payload) {
  return fetchJson(`${BIZ}/model-configs`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
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
