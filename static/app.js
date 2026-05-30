const state = {
  mode: "single",
  status: null,
  selectedProduct: null
};

const insightStorageKey = "yazioOverview.insightSelection";

const insightSorts = {
  search: [
    ["amount", "Menge absteigend"],
    ["calories", "Kalorien absteigend"],
    ["protein", "Protein absteigend"],
    ["name", "Name A-Z"]
  ],
  "top-amount": [["amount", "Menge absteigend"]],
  "top-calories": [["calories", "Kalorien absteigend"]],
  "top-protein": [["protein", "Protein absteigend"]],
  "days-calories": [
    ["energy:desc", "Kalorien absteigend"],
    ["energy:asc", "Kalorien aufsteigend"]
  ],
  "days-protein": [
    ["protein:desc", "Protein absteigend"],
    ["protein:asc", "Protein aufsteigend"]
  ],
  meals: [
    ["energy", "Kalorien absteigend"],
    ["protein", "Protein absteigend"],
    ["count", "Einträge absteigend"]
  ],
  weekdays: [
    ["energy", "Kalorien absteigend"],
    ["protein", "Protein absteigend"],
    ["count", "Tage absteigend"]
  ],
  months: [
    ["energy", "Kalorien absteigend"],
    ["protein", "Protein absteigend"],
    ["count", "Tage absteigend"]
  ]
};

const els = {
  menuToggle: document.querySelector("#menuToggle"),
  mainNav: document.querySelector("#mainNav"),
  currentPageLabel: document.querySelector("#currentPageLabel"),
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
  previousDay: document.querySelector("#previousDay"),
  nextDay: document.querySelector("#nextDay"),
  fromDate: document.querySelector("#fromDate"),
  toDate: document.querySelector("#toDate"),
  insightForm: document.querySelector("#insightForm"),
  insightType: document.querySelector("#insightType"),
  insightSearch: document.querySelector("#insightSearch"),
  insightSearchLabel: document.querySelector("#insightSearchLabel"),
  insightSort: document.querySelector("#insightSort"),
  insightSummary: document.querySelector("#insightSummary"),
  insightResults: document.querySelector("#insightResults"),
  productDetail: document.querySelector("#productDetail"),
  productDetailTitle: document.querySelector("#productDetailTitle"),
  productDaySort: document.querySelector("#productDaySort"),
  productDayResults: document.querySelector("#productDayResults"),
  results: document.querySelector("#results"),
  emptyState: document.querySelector("#emptyState"),
  message: document.querySelector("#message"),
  dayTemplate: document.querySelector("#dayTemplate"),
  mealTemplate: document.querySelector("#mealTemplate")
};

document.querySelectorAll(".nav-tab").forEach((tab) => {
  tab.addEventListener("click", () => showPage(tab.dataset.page));
});

els.menuToggle.addEventListener("click", () => {
  const isOpen = els.mainNav.classList.toggle("open");
  els.menuToggle.setAttribute("aria-expanded", String(isOpen));
  els.menuToggle.setAttribute("aria-label", isOpen ? "Menü schließen" : "Menü öffnen");
});

document.addEventListener("click", (event) => {
  if (!els.mainNav.classList.contains("open")) {
    return;
  }
  if (els.mainNav.contains(event.target) || els.menuToggle.contains(event.target)) {
    return;
  }
  closeMenu();
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

els.insightType.addEventListener("change", () => {
  populateInsightSort();
  persistInsightSelection();
  clearInsightResults();
});

els.insightSort.addEventListener("change", () => {
  persistInsightSelection();
  loadInsights().catch((error) => showMessage(error.message));
});

els.insightSearch.addEventListener("input", persistInsightSelection);

els.insightForm.addEventListener("submit", (event) => {
  event.preventDefault();
  persistInsightSelection();
  loadInsights().catch((error) => showMessage(error.message));
});

els.productDaySort.addEventListener("change", () => {
  if (state.selectedProduct) {
    loadProductDays(state.selectedProduct).catch((error) => showMessage(error.message));
  }
});

document.querySelectorAll(".today-button").forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.querySelector(`#${button.dataset.target}`);
    if (target) target.value = todayIso();
    updateDayNavButtons();
  });
});

els.previousDay.addEventListener("click", () => shiftSingleDay(-1));
els.nextDay.addEventListener("click", () => shiftSingleDay(1));

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
  await showSingleDay(els.singleDate.value);
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
  updateDayNavButtons();
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

async function showSingleDay(date) {
  if (!date) {
    return;
  }
  try {
    clearMessage();
    els.singleDate.value = date;
    updateDayNavButtons();
    const response = await fetch(`/api/day?date=${encodeURIComponent(date)}`);
    const payload = await readJson(response);
    renderDays([payload]);
  } catch (error) {
    showMessage(error.message);
  }
}

function shiftSingleDay(offset) {
  const current = parseIsoDate(els.singleDate.value);
  if (!current) {
    return;
  }
  current.setDate(current.getDate() + offset);
  showSingleDay(toIsoDate(current));
}

function updateDayNavButtons() {
  const current = parseIsoDate(els.singleDate.value);
  const min = parseIsoDate(els.singleDate.min);
  const max = parseIsoDate(els.singleDate.max);
  els.previousDay.disabled = Boolean(current && min && current <= min);
  els.nextDay.disabled = Boolean(current && max && current >= max);
}

function restoreInsightSelection() {
  const saved = JSON.parse(localStorage.getItem(insightStorageKey) ?? "{}");
  if (saved.type && insightSorts[saved.type]) {
    els.insightType.value = saved.type;
  }
  els.insightSearch.value = saved.search ?? "";
  populateInsightSort(saved.sort);
}

function persistInsightSelection() {
  localStorage.setItem(insightStorageKey, JSON.stringify({
    type: els.insightType.value,
    search: els.insightSearch.value,
    sort: els.insightSort.value
  }));
}

function populateInsightSort(preferred) {
  const options = insightSorts[els.insightType.value] ?? insightSorts.search;
  els.insightSort.replaceChildren(...options.map(([value, label]) => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    return option;
  }));
  els.insightSort.value = preferred && options.some(([value]) => value === preferred) ? preferred : options[0][0];
  els.insightSearchLabel.classList.toggle("hidden", els.insightType.value !== "search");
}

function clearInsightResults() {
  els.insightSummary.textContent = "";
  els.insightResults.replaceChildren();
  els.productDetail.classList.add("hidden");
  els.productDayResults.replaceChildren();
  state.selectedProduct = null;
}

async function loadInsights() {
  clearMessage();
  clearInsightResults();
  const type = els.insightType.value;
  if (type.startsWith("top-") || type === "search") {
    const sort = type === "top-calories" ? "calories" : type === "top-protein" ? "protein" : els.insightSort.value;
    const params = new URLSearchParams({ sort, limit: "100" });
    if (type === "search") {
      params.set("query", els.insightSearch.value);
    }
    const payload = await readJson(await fetch(`/api/insights/products?${params}`));
    renderProductList(payload.items, type === "search" ? "Produktsuche" : "Top 100 Lebensmittel");
    return;
  }
  if (type.startsWith("days-")) {
    const [sort, dir] = els.insightSort.value.split(":");
    const payload = await readJson(await fetch(`/api/insights/days?sort=${encodeURIComponent(sort)}&dir=${encodeURIComponent(dir)}`));
    renderDayRanking(payload.days);
    return;
  }
  const payload = await readJson(await fetch(`/api/insights/${type}?sort=${encodeURIComponent(els.insightSort.value)}`));
  renderMacroAggregation(payload.items);
}

function renderProductList(items, title) {
  els.insightSummary.textContent = `${title}: ${items.length} Einträge`;
  const table = insightTable(["Lebensmittel", "Menge", "Tage", "Kalorien", "Protein", ""]);
  const body = table.querySelector("tbody");
  for (const item of items) {
    const row = document.createElement("tr");
    row.className = "clickable-row";
    row.title = "Verzehrtage anzeigen";
    row.addEventListener("click", () => loadProductDays(item).catch((error) => showMessage(error.message)));
    row.append(
      td(productLabel(item)),
      td(item.amountText, "number-cell"),
      td(String(item.dayCount), "number-cell"),
      td(`${fmt(item.macro.energy)} kcal`, "number-cell"),
      td(`${fmt(item.macro.protein)} g`, "number-cell"),
      actionTd("Tage", (event) => {
        event.stopPropagation();
        loadProductDays(item).catch((error) => showMessage(error.message));
      })
    );
    body.append(row);
  }
  els.insightResults.append(table);
}

async function loadProductDays(item) {
  state.selectedProduct = item;
  const params = new URLSearchParams({ key: item.key, sort: els.productDaySort.value });
  const payload = await readJson(await fetch(`/api/insights/product-days?${params}`));
  els.productDetail.classList.remove("hidden");
  els.productDetailTitle.textContent = productLabel(item);
  const table = insightTable(["Tag", "Menge", "Kalorien", "Protein", "Einträge", ""]);
  const body = table.querySelector("tbody");
  for (const day of payload.days) {
    const row = document.createElement("tr");
    row.append(
      td(formatDate(day.date)),
      td(day.amountText, "number-cell"),
      td(`${fmt(day.macro.energy)} kcal`, "number-cell"),
      td(`${fmt(day.macro.protein)} g`, "number-cell"),
      td(String(day.count), "number-cell"),
      actionTd("Öffnen", () => openDay(day.date))
    );
    body.append(row);
  }
  els.productDayResults.replaceChildren(table);
  els.productDetail.scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderDayRanking(days) {
  els.insightSummary.textContent = `Tage: ${days.length} Einträge`;
  const table = insightTable(["Tag", "Kalorien", "KH", "Protein", "Fett", ""]);
  const body = table.querySelector("tbody");
  for (const day of days) {
    const row = document.createElement("tr");
    row.append(
      td(formatDate(day.date)),
      td(`${fmt(day.energy)} kcal`, "number-cell"),
      td(`${fmt(day.carbs)} g`, "number-cell"),
      td(`${fmt(day.protein)} g`, "number-cell"),
      td(`${fmt(day.fat)} g`, "number-cell"),
      actionTd("Öffnen", () => openDay(day.date))
    );
    body.append(row);
  }
  els.insightResults.append(table);
}

function renderMacroAggregation(items) {
  els.insightSummary.textContent = `${items.length} verdichtete Einträge`;
  const table = insightTable(["Gruppe", "Einträge", "Gesamt kcal", "Ø kcal", "Ø Protein", "Ø Fett"]);
  const body = table.querySelector("tbody");
  for (const item of items) {
    const row = document.createElement("tr");
    row.append(
      td(item.label),
      td(String(item.count), "number-cell"),
      td(`${fmt(item.total.energy)} kcal`, "number-cell"),
      td(`${fmt(item.average.energy)} kcal`, "number-cell"),
      td(`${fmt(item.average.protein)} g`, "number-cell"),
      td(`${fmt(item.average.fat)} g`, "number-cell")
    );
    body.append(row);
  }
  els.insightResults.append(table);
}

function insightTable(headers) {
  const table = document.createElement("table");
  table.className = "insight-table";
  const head = document.createElement("thead");
  const headRow = document.createElement("tr");
  for (const header of headers) {
    const th = document.createElement("th");
    th.textContent = header;
    headRow.append(th);
  }
  head.append(headRow);
  table.append(head, document.createElement("tbody"));
  return table;
}

function td(text, className = "") {
  const cell = document.createElement("td");
  cell.textContent = text ?? "";
  if (className) cell.className = className;
  return cell;
}

function actionTd(label, handler) {
  const cell = document.createElement("td");
  const button = document.createElement("button");
  button.type = "button";
  button.className = "secondary";
  button.textContent = label;
  button.addEventListener("click", handler);
  cell.append(button);
  return cell;
}

function productLabel(item) {
  return item.producer ? `${item.name} · ${item.producer}` : item.name;
}

function openDay(date) {
  window.open(`/?date=${encodeURIComponent(date)}`, "_blank", "noopener");
}

async function openDateFromUrl() {
  const date = new URLSearchParams(window.location.search).get("date");
  if (!date) {
    return;
  }
  showPage("analysis");
  await showSingleDay(date);
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
    const classification = document.createElement("select");
    classification.className = "classification-select";
    classification.title = "Zuordnung für Export: gegessen oder getrunken";
    classification.append(
      option("food", "gegessen"),
      option("drink", "getrunken")
    );
    classification.value = item.classification ?? item.automaticClassification ?? "food";
    classification.classList.toggle("overridden", Boolean(item.classificationOverridden));
    classification.addEventListener("change", () => {
      saveItemClassification(item, classification).catch((error) => showMessage(error.message));
    });
    row.append(left, macros, classification);
    list.append(row);
  }
  return node;
}

function option(value, label) {
  const node = document.createElement("option");
  node.value = value;
  node.textContent = label;
  return node;
}

async function saveItemClassification(item, select) {
  if (!item.itemId) {
    showMessage("Dieser Eintrag hat keine stabile ID und kann nicht korrigiert werden.");
    select.value = item.classification ?? item.automaticClassification ?? "food";
    return;
  }
  const response = await fetch("/api/item-classification", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      itemId: item.itemId,
      classification: select.value
    })
  });
  const payload = await readJson(response);
  item.classification = payload.classification;
  item.automaticClassification = payload.automaticClassification;
  item.classificationOverridden = payload.classificationOverridden;
  select.classList.toggle("overridden", Boolean(payload.classificationOverridden));
  showMessage(payload.classificationOverridden ? "Zuordnung gespeichert." : "Zuordnung auf Automatik zurückgesetzt.", "ok");
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

function parseIsoDate(value) {
  return value ? new Date(`${value}T12:00:00`) : null;
}

function toIsoDate(date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
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
  let label = "";
  document.querySelectorAll(".nav-tab").forEach((item) => {
    const active = item.dataset.page === pageName;
    item.classList.toggle("active", active);
    if (active) {
      label = item.textContent.trim();
    }
  });
  document.querySelectorAll(".page-section").forEach((page) => page.classList.toggle("active", page.id === `page-${pageName}`));
  if (label) {
    els.currentPageLabel.textContent = label;
  }
  closeMenu();
  clearMessage();
}

function closeMenu() {
  els.mainNav.classList.remove("open");
  els.menuToggle.setAttribute("aria-expanded", "false");
  els.menuToggle.setAttribute("aria-label", "Menü öffnen");
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

restoreInsightSelection();
loadStatus()
  .then(openDateFromUrl)
  .catch((error) => showMessage(error.message));
