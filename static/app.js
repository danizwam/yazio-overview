const state = {
  mode: "single",
  status: null
};

const els = {
  statusText: document.querySelector("#statusText"),
  statusDot: document.querySelector(".status-dot"),
  productCount: document.querySelector("#productCount"),
  dayCount: document.querySelector("#dayCount"),
  dateRange: document.querySelector("#dateRange"),
  uploadForm: document.querySelector("#uploadForm"),
  syncForm: document.querySelector("#syncForm"),
  settingsForm: document.querySelector("#settingsForm"),
  profileName: document.querySelector("#profileName"),
  birthDate: document.querySelector("#birthDate"),
  yazioUsername: document.querySelector("#yazioUsername"),
  yazioPassword: document.querySelector("#yazioPassword"),
  syncFromDate: document.querySelector("#syncFromDate"),
  syncToDate: document.querySelector("#syncToDate"),
  syncLogPanel: document.querySelector("#syncLogPanel"),
  syncLogState: document.querySelector("#syncLogState"),
  syncLog: document.querySelector("#syncLog"),
  singleForm: document.querySelector("#singleForm"),
  rangeForm: document.querySelector("#rangeForm"),
  singleExports: document.querySelector("#singleExports"),
  rangeExports: document.querySelector("#rangeExports"),
  singleDate: document.querySelector("#singleDate"),
  fromDate: document.querySelector("#fromDate"),
  toDate: document.querySelector("#toDate"),
  results: document.querySelector("#results"),
  emptyState: document.querySelector("#emptyState"),
  message: document.querySelector("#message"),
  dayTemplate: document.querySelector("#dayTemplate"),
  mealTemplate: document.querySelector("#mealTemplate")
};

document.querySelectorAll(".nav-tab").forEach((tab) => {
  tab.addEventListener("click", () => showPage(tab.dataset.page));
});

document.querySelectorAll("[data-goto]").forEach((button) => {
  button.addEventListener("click", () => showPage(button.dataset.goto));
});

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    state.mode = tab.dataset.mode;
    document.querySelectorAll(".tab").forEach((item) => item.classList.toggle("active", item === tab));
    els.singleForm.classList.toggle("hidden", state.mode !== "single");
    els.rangeForm.classList.toggle("hidden", state.mode !== "range");
    els.singleExports.classList.toggle("hidden", state.mode !== "single");
    els.rangeExports.classList.toggle("hidden", state.mode !== "range");
    clearMessage();
  });
});

document.querySelectorAll("[data-export]").forEach((button) => {
  button.addEventListener("click", () => exportFile(button.dataset.scope, button.dataset.export));
});

document.querySelectorAll(".today-button").forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.querySelector(`#${button.dataset.target}`);
    if (target) target.value = todayIso();
  });
});

els.settingsForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const response = await fetch("/api/settings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: els.profileName.value,
        birthDate: els.birthDate.value,
        username: els.yazioUsername.value,
        password: els.yazioPassword.value
      })
    });
    await readJson(response);
    els.yazioPassword.value = "";
    await loadStatus();
    showMessage("Einstellungen gespeichert.", "ok");
  } catch (error) {
    showMessage(error.message);
  }
});

els.syncForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!els.syncFromDate.value || !els.syncToDate.value) return;
  try {
    setBusy("Synchronisiere Yazio...");
    showMessage("Synchronisierung läuft. Das kann je nach Zeitraum etwas dauern.", "ok");
    showSyncLog({ status: "running", logs: ["Starte Synchronisierung..."], running: true });
    const response = await fetch("/api/sync", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        from: els.syncFromDate.value,
        to: els.syncToDate.value
      })
    });
    const payload = await readJson(response);
    showSyncLog(payload);
    pollSyncStatus();
  } catch (error) {
    showMessage(error.message);
  }
});

els.uploadForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(els.uploadForm);
  const hasProducts = form.get("products")?.size > 0;
  const hasDays = form.get("days")?.size > 0;
  if (!hasProducts && !hasDays) {
    showMessage("Bitte mindestens eine JSON-Datei auswählen.");
    return;
  }
  try {
    setBusy("Speichere Upload...");
    const response = await fetch("/api/upload", { method: "POST", body: form });
    const payload = await readJson(response);
    await loadStatus();
    showMessage(`Gespeichert: ${payload.updated.join(", ")}`, "ok");
  } catch (error) {
    showMessage(error.message);
  }
});

els.singleForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const date = els.singleDate.value;
  if (!date) return;
  try {
    clearMessage();
    const response = await fetch(`/api/day?date=${encodeURIComponent(date)}`);
    const payload = await readJson(response);
    renderDays([payload]);
  } catch (error) {
    showMessage(error.message);
  }
});

els.rangeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const from = els.fromDate.value;
  const to = els.toDate.value;
  if (!from || !to) return;
  try {
    clearMessage();
    const response = await fetch(`/api/range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    const payload = await readJson(response);
    renderDays(payload.days);
    if (payload.days.length === 0) {
      showMessage("In diesem Zeitraum wurden keine Tage gefunden.");
    }
  } catch (error) {
    showMessage(error.message);
  }
});

async function loadStatus() {
  const response = await fetch("/api/status");
  const status = await readJson(response);
  state.status = status;
  els.statusDot.classList.toggle("ready", status.hasProducts && status.hasDays && !status.error);
  els.statusText.textContent = status.error
    ? "Datenfehler"
    : status.hasProducts && status.hasDays
      ? "Bereit"
      : "Daten importieren";
  els.productCount.textContent = String(status.productCount ?? 0);
  els.dayCount.textContent = String(status.dayCount ?? 0);
  els.dateRange.textContent = status.firstDate && status.lastDate
    ? `${formatDate(status.firstDate)} bis ${formatDate(status.lastDate)}`
    : "-";
  fillSettings(status.settings ?? {});
  for (const input of [els.singleDate, els.fromDate, els.toDate]) {
    if (status.firstDate) input.min = status.firstDate;
    if (status.lastDate) input.max = status.lastDate;
  }
  if (status.lastDate && !els.singleDate.value) els.singleDate.value = status.lastDate;
  if (status.firstDate && !els.fromDate.value) els.fromDate.value = status.firstDate;
  if (status.lastDate && !els.toDate.value) els.toDate.value = status.lastDate;
  if (!els.syncFromDate.value) els.syncFromDate.value = status.recommendedSyncFrom ?? todayIso();
  if (!els.syncToDate.value) els.syncToDate.value = status.recommendedSyncTo ?? todayIso();
  els.emptyState.classList.toggle("hidden", status.hasProducts || status.hasDays);
  if (status.error) showMessage(status.error);
}

function fillSettings(settings) {
  els.profileName.value = settings.name ?? "";
  els.birthDate.value = settings.birthDate ?? "";
  els.yazioUsername.value = settings.username ?? "";
  els.yazioPassword.placeholder = settings.hasPassword ? "Gespeichertes Passwort bleibt erhalten" : "";
}

async function pollSyncStatus() {
  try {
    const response = await fetch("/api/sync/status");
    const payload = await readJson(response);
    showSyncLog(payload);
    if (payload.running) {
      window.setTimeout(pollSyncStatus, 1000);
      return;
    }
    await loadStatus();
    if (payload.status === "success") {
      showMessage(`Synchronisiert: ${payload.dayCount} Tage, ${payload.productCount} Produkte.`, "ok");
    } else if (payload.status === "error") {
      showMessage(`Yazio-Sync fehlgeschlagen: ${payload.error}`);
    }
  } catch (error) {
    showMessage(error.message);
  }
}

function showSyncLog(payload) {
  els.syncLogPanel.classList.remove("hidden");
  const logs = payload.logs ?? [];
  els.syncLog.textContent = logs.join("\n");
  els.syncLogState.textContent = payload.running
    ? "läuft"
    : payload.status === "success"
      ? "fertig"
      : payload.status === "error"
        ? "Fehler"
        : "bereit";
  els.syncLog.scrollTop = els.syncLog.scrollHeight;
}

function renderDays(days) {
  els.results.replaceChildren();
  for (const day of days) {
    const node = els.dayTemplate.content.firstElementChild.cloneNode(true);
    node.querySelector("h2").textContent = formatDate(day.date);
    node.querySelector(".macro-strip").append(...macroPills(day.total));
    node.querySelector(".copy-day").addEventListener("click", () => copy(day.copyText));
    const note = node.querySelector(".day-note");
    note.value = day.note ?? "";
    note.addEventListener("change", () => saveNote(day.date, note.value));
    const meals = node.querySelector(".meals");
    for (const meal of day.meals) {
      meals.append(renderMeal(meal));
    }
    els.results.append(node);
  }
}

async function saveNote(date, note) {
  try {
    const response = await fetch(`/api/note?date=${encodeURIComponent(date)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ note })
    });
    await readJson(response);
    showMessage("Besonderheit gespeichert.", "ok");
  } catch (error) {
    showMessage(error.message);
  }
}

function renderMeal(meal) {
  const node = els.mealTemplate.content.firstElementChild.cloneNode(true);
  node.querySelector("h3").textContent = meal.label;
  node.querySelector(".meal-total").append(...macroPills(meal.total));
  node.querySelector(".copy-meal").addEventListener("click", () => copy(meal.copyText));
  const list = node.querySelector(".food-list");
  for (const item of meal.items) {
    const row = document.createElement("div");
    row.className = "food-row";
    const left = document.createElement("div");
    const name = document.createElement("div");
    name.className = "food-name";
    name.textContent = item.producer ? `${item.name} · ${item.producer}` : item.name;
    const meta = document.createElement("div");
    meta.className = "food-meta";
    meta.textContent = item.amountLabel ?? `${fmt(item.amount)} ${item.baseUnit ?? ""}`.trim();
    left.append(name, meta);

    const macros = document.createElement("div");
    macros.className = "food-macros";
    macros.textContent = `${fmt(item.macro.energy)} kcal · KH ${fmt(item.macro.carbs)} g · P ${fmt(item.macro.protein)} g · F ${fmt(item.macro.fat)} g`;
    row.append(left, macros);
    list.append(row);
  }
  return node;
}

function macroPills(macro) {
  return [
    pill(fmt(macro.energy), "kcal"),
    pill(fmt(macro.carbs), "KH g"),
    pill(fmt(macro.protein), "Protein g"),
    pill(fmt(macro.fat), "Fett g"),
    pill(fmt(macro.sugar), "Zucker g"),
    pill(fmt(macro.fiber), "Ballaststoffe g")
  ];
}

function pill(value, label) {
  const node = document.createElement("div");
  node.className = "pill";
  node.textContent = value;
  const suffix = document.createElement("span");
  suffix.textContent = label;
  node.append(suffix);
  return node;
}

async function copy(text) {
  try {
    if (navigator.clipboard?.writeText && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
    } else {
      fallbackCopy(text);
    }
    showMessage("In die Zwischenablage kopiert.", "ok");
  } catch (error) {
    fallbackCopy(text);
    showMessage("In die Zwischenablage kopiert.", "ok");
  }
}

function fallbackCopy(text) {
  const textarea = document.createElement("textarea");
  textarea.value = text;
  textarea.setAttribute("readonly", "");
  textarea.style.position = "fixed";
  textarea.style.left = "-9999px";
  document.body.append(textarea);
  textarea.select();
  document.execCommand("copy");
  textarea.remove();
}

function exportFile(scope, type) {
  const params = new URLSearchParams();
  if (scope === "single") {
    if (!els.singleDate.value) {
      showMessage("Bitte zuerst ein Datum auswählen.");
      return;
    }
    params.set("date", els.singleDate.value);
  } else {
    if (!els.fromDate.value || !els.toDate.value) {
      showMessage("Bitte zuerst einen Zeitraum auswählen.");
      return;
    }
    params.set("from", els.fromDate.value);
    params.set("to", els.toDate.value);
  }
  window.location.href = `/api/export/${type}?${params.toString()}`;
}

function todayIso() {
  const now = new Date();
  const offset = now.getTimezoneOffset();
  return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10);
}

function setBusy(text) {
  els.statusText.textContent = text;
}

function showMessage(text, kind = "error") {
  els.message.textContent = text;
  els.message.classList.remove("hidden");
  els.message.style.background = kind === "ok" ? "#edf9f2" : "#fff4ec";
  els.message.style.borderColor = kind === "ok" ? "#bee8cf" : "#f2d1bd";
  els.message.style.color = kind === "ok" ? "#155d3c" : "#7d3219";
}

function clearMessage() {
  els.message.classList.add("hidden");
}

function showPage(pageName) {
  document.querySelectorAll(".nav-tab").forEach((item) => item.classList.toggle("active", item.dataset.page === pageName));
  document.querySelectorAll(".page-section").forEach((page) => page.classList.toggle("active", page.id === `page-${pageName}`));
  clearMessage();
}

async function readJson(response) {
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.error ?? `HTTP ${response.status}`);
  }
  return payload;
}

function formatDate(value) {
  return new Intl.DateTimeFormat("de-DE", {
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(new Date(`${value}T12:00:00`));
}

function fmt(value) {
  const rounded = Number(value ?? 0);
  return new Intl.NumberFormat("de-DE", {
    maximumFractionDigits: Math.abs(rounded - Math.round(rounded)) < 0.01 ? 0 : 1
  }).format(rounded);
}

loadStatus().catch((error) => showMessage(error.message));
