import { computed, ref } from "vue";
import { listSpaces } from "../api";

const LS_KEY = "mienmien.currentSpaceId";
const spaces = ref([]);
const currentSpaceId = ref(localStorage.getItem(LS_KEY) || "");
const loading = ref(false);

function persistCurrentSpace(spaceId) {
  currentSpaceId.value = spaceId || "";
  if (spaceId) {
    localStorage.setItem(LS_KEY, spaceId);
  } else {
    localStorage.removeItem(LS_KEY);
  }
}

async function refreshSpaces() {
  loading.value = true;
  try {
    const rows = await listSpaces();
    spaces.value = Array.isArray(rows) ? rows : [];
    if (!currentSpaceId.value && spaces.value.length > 0) {
      persistCurrentSpace(spaces.value[0].spaceId);
    }
    if (currentSpaceId.value) {
      const exists = spaces.value.some((x) => x.spaceId === currentSpaceId.value);
      if (!exists) {
        persistCurrentSpace(spaces.value[0]?.spaceId || "");
      }
    }
  } finally {
    loading.value = false;
  }
}

const currentSpace = computed(() => spaces.value.find((x) => x.spaceId === currentSpaceId.value) || null);

export function useWorkspace() {
  return {
    spaces,
    currentSpaceId,
    currentSpace,
    loading,
    refreshSpaces,
    setCurrentSpace: persistCurrentSpace
  };
}
