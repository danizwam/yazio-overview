const state = {
  mode: "single",
  status: null,
  selectedProduct: null,
  rangeDays: [],
  chartVisible: false,
  chartMetric: "energy",
  auth: null,
  rangeInitialized: false,
  rangeDefaultPending: false
};

const insightStorageKey = "yazioOverview.insightSelection";

const chartMetrics = {
  energy: {
    label: "Kalorien",
    title: "Konsumierte Kalorien pro Tag",
    unit: "kcal",
    value: (day) => Number(day.total?.energy ?? day.daily?.energy ?? 0),
    goal: (day) => Number(day.daily?.energyGoal ?? 0)
  },
  protein: {
    label: "Protein",
    title: "Protein pro Tag",
    unit: "g",
    value: (day) => Number(day.total?.protein ?? 0)
  },
  carbs: {
    label: "Kohlenhydrate",
    title: "Kohlenhydrate pro Tag",
    unit: "g",
    value: (day) => Number(day.total?.carbs ?? 0)
  },
  fat: {
    label: "Fett",
    title: "Fett pro Tag",
    unit: "g",
    value: (day) => Number(day.total?.fat ?? 0)
  }
};

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
  loginScreen: document.querySelector("#loginScreen"),
  loginForm: document.querySelector("#loginForm"),
  loginUsername: document.querySelector("#loginUsername"),
  loginPassword: document.querySelector("#loginPassword"),
  loginMessage: document.querySelector("#loginMessage"),
  appShell: document.querySelector("#appShell"),
  menuToggle: document.querySelector("#menuToggle"),
  mainNav: document.querySelector("#mainNav"),
  currentPageLabel: document.querySelector("#currentPageLabel"),
  statusText: document.querySelector("#statusText"),
  statusDot: document.querySelector(".status-dot"),
  currentUser: document.querySelector("#currentUser"),
  logoutButton: document.querySelector("#logoutButton"),
  usersNav: document.querySelector("#usersNav"),
  passwordPanel: document.querySelector("#passwordPanel"),
  passwordForm: document.querySelector("#passwordForm"),
  passwordHint: document.querySelector("#passwordHint"),
  currentPassword: document.querySelector("#currentPassword"),
  newPassword: document.querySelector("#newPassword"),
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
  toggleCalorieChart: document.querySelector("#toggleCalorieChart"),
  chartMetric: document.querySelector("#chartMetric"),
  chartTitle: document.querySelector("#chartTitle"),
  rangeDashboard: document.querySelector("#rangeDashboard"),
  chartLegend: document.querySelector("#chartLegend"),
  calorieChartPanel: document.querySelector("#calorieChartPanel"),
  calorieChart: document.querySelector("#calorieChart"),
  dayComparePanel: document.querySelector("#dayComparePanel"),
  compareDayA: document.querySelector("#compareDayA"),
  compareDayB: document.querySelector("#compareDayB"),
  compareResult: document.querySelector("#compareResult"),
  weekSummaryPanel: document.querySelector("#weekSummaryPanel"),
  weekSummary: document.querySelector("#weekSummary"),
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
  appVersion: document.querySelector("#appVersion"),
  loadClassificationRules: document.querySelector("#loadClassificationRules"),
  classificationRules: document.querySelector("#classificationRules"),
  loadDataQuality: document.querySelector("#loadDataQuality"),
  dataQualityResults: document.querySelector("#dataQualityResults"),
  downloadBackup: document.querySelector("#downloadBackup"),
  restoreForm: document.querySelector("#restoreForm"),
  userForm: document.querySelector("#userForm"),
  adminPasswordWarning: document.querySelector("#adminPasswordWarning"),
  newUserName: document.querySelector("#newUserName"),
  newUsername: document.querySelector("#newUsername"),
  newUserPassword: document.querySelector("#newUserPassword"),
  userResults: document.querySelector("#userResults"),
  results: document.querySelector("#results"),
  emptyState: document.querySelector("#emptyState"),
  message: document.querySelector("#message"),
  dayTemplate: document.querySelector("#dayTemplate"),
  mealTemplate: document.querySelector("#mealTemplate")
};

document.querySelectorAll(".nav-tab").forEach((tab) => {
  tab.addEventListener("click", () => showPage(tab.dataset.page));
});

els.loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const payload = await readJson(await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: els.loginUsername.value,
        password: els.loginPassword.value
      })
    }));
    state.auth = { userManagement: true, loggedIn: true, user: payload.user };
    await showAuthenticatedApp();
  } catch (error) {
    els.loginMessage.textContent = error.message;
  }
});

els.logoutButton.addEventListener("click", async () => {
  await fetch("/api/logout", { method: "POST" });
  state.auth = { userManagement: true, loggedIn: false, user: null };
  showLogin("Du wurdest abgemeldet.");
});

els.passwordForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    await readJson(await fetch("/api/password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        currentPassword: els.currentPassword.value,
        newPassword: els.newPassword.value
      })
    }));
    els.currentPassword.value = "";
    els.newPassword.value = "";
    showMessage("Passwort wurde gespeichert.");
  } catch (error) {
    showMessage(error.message);
  }
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
    if (state.mode !== "range") {
      hideRangeEnhancements();
    } else {
      updateCalorieChartControls();
    }
    clearMessage();
  });
});

document.querySelectorAll("[data-export]").forEach((button) => {
  button.addEventListener("click", () => exportFile(button.dataset.scope, button.dataset.export));
});

els.toggleCalorieChart.addEventListener("click", () => {
  state.chartVisible = !state.chartVisible;
  renderCalorieChart();
});

els.chartMetric.addEventListener("change", () => {
  state.chartMetric = els.chartMetric.value;
  renderCalorieChart();
});

els.compareDayA.addEventListener("change", renderDayComparison);
els.compareDayB.addEventListener("change", renderDayComparison);

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
    updateRangeConstraints();
    updateDayNavButtons();
  });
});

els.previousDay.addEventListener("click", () => shiftSingleDay(-1));
els.nextDay.addEventListener("click", () => shiftSingleDay(1));
els.fromDate.addEventListener("focus", cancelPendingRangeDefault);
els.toDate.addEventListener("focus", cancelPendingRangeDefault);
els.fromDate.addEventListener("input", cancelPendingRangeDefault);
els.toDate.addEventListener("input", cancelPendingRangeDefault);
els.fromDate.addEventListener("change", () => {
  cancelPendingRangeDefault();
  updateRangeConstraints();
});
els.toDate.addEventListener("change", () => {
  cancelPendingRangeDefault();
  updateRangeConstraints();
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
    els.yazioPassword.value = state.status?.demoMode ? (state.status.demoPassword ?? "passwordMock123") : "";
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

els.loadClassificationRules.addEventListener("click", () => loadClassificationRules().catch((error) => showMessage(error.message)));
els.loadDataQuality.addEventListener("click", () => loadDataQuality().catch((error) => showMessage(error.message)));
els.downloadBackup.addEventListener("click", () => {
  window.location.href = "/api/backup";
});

els.restoreForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(els.restoreForm);
  if (!form.get("backup")?.size) {
    showMessage("Bitte ein Backup-ZIP auswÃ¤hlen.");
    return;
  }
  try {
    const payload = await readJson(await fetch("/api/restore", { method: "POST", body: form }));
    await loadStatus();
    showMessage(`Backup wiederhergestellt: ${payload.restored.length} Dateien.`, "ok");
  } catch (error) {
    showMessage(error.message);
  }
});

els.userForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const payload = await readJson(await fetch("/api/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        action: "create",
        name: els.newUserName.value,
        username: els.newUsername.value,
        password: els.newUserPassword.value
      })
    }));
    els.userForm.reset();
    renderUsers(payload.items ?? []);
    showMessage("Benutzer angelegt.", "ok");
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
  if (!validRange(from, to)) {
    showMessage("Das Startdatum darf nicht nach dem Enddatum liegen.");
    return;
  }
  try {
    clearMessage();
    const response = await fetch(`/api/range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    const payload = await readJson(response);
    state.rangeDays = payload.days;
    state.chartVisible = false;
    renderDays(payload.days);
    renderRangeEnhancements();
    updateCalorieChartControls();
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
      ? status.demoMode ? "Demo bereit" : "Bereit"
      : "Daten importieren";
  els.productCount.textContent = String(status.productCount ?? 0);
  els.dayCount.textContent = String(status.dayCount ?? 0);
  els.dateRange.textContent = status.firstDate && status.lastDate
    ? `${formatDate(status.firstDate)} bis ${formatDate(status.lastDate)}`
    : "-";
  const version = status.version?.number && status.version.number !== "dev" ? `v${status.version.number}` : "dev";
  els.appVersion.textContent = version;
  fillSettings(status.settings ?? {});
  if (status.firstDate) {
    els.singleDate.min = status.firstDate;
    els.fromDate.min = status.firstDate;
    els.toDate.min = status.firstDate;
  }
  if (status.lastDate) {
    els.singleDate.max = status.lastDate;
  }
  els.fromDate.max = todayIso();
  els.toDate.max = todayIso();
  if (status.lastDate && !els.singleDate.value) els.singleDate.value = status.lastDate;
  initializeRangeDates(status);
  if (!els.syncFromDate.value) els.syncFromDate.value = status.recommendedSyncFrom ?? todayIso();
  if (!els.syncToDate.value) els.syncToDate.value = status.recommendedSyncTo ?? todayIso();
  updateRangeConstraints();
  updateDayNavButtons();
  els.emptyState.classList.toggle("hidden", status.hasProducts || status.hasDays);
  if (status.error) showMessage(status.error);
}

function fillSettings(settings) {
  els.profileName.value = settings.name ?? "";
  els.birthDate.value = settings.birthDate ?? "";
  els.yazioUsername.value = settings.username ?? "";
  if (state.status?.demoMode) {
    els.yazioPassword.value = state.status.demoPassword ?? "passwordMock123";
    els.yazioPassword.placeholder = "Demo-Passwort";
  } else {
    els.yazioPassword.placeholder = settings.hasPassword ? "Gespeichertes Passwort bleibt erhalten" : "";
  }
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
    node.id = `day-${day.date}`;
    node.dataset.date = day.date;
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
    state.rangeDays = [];
    hideRangeEnhancements();
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

function updateRangeConstraints() {
  const firstDate = state.status?.firstDate ?? "";
  const today = todayIso();
  els.fromDate.min = firstDate;
  els.fromDate.max = els.toDate.value || today;
  els.toDate.min = els.fromDate.value || firstDate;
  els.toDate.max = today;
}

function updateCalorieChartControls() {
  const canShow = state.mode === "range" && state.rangeDays.length >= 2;
  els.toggleCalorieChart.classList.toggle("hidden", !canShow);
  if (!canShow) {
    hideCalorieChart();
    return;
  }
  els.toggleCalorieChart.textContent = state.chartVisible ? "Graph ausblenden" : "Graph anzeigen";
  renderCalorieChart();
}

function hideCalorieChart() {
  state.chartVisible = false;
  els.toggleCalorieChart.classList.add("hidden");
  els.toggleCalorieChart.textContent = "Graph anzeigen";
  els.calorieChartPanel.classList.add("hidden");
  els.calorieChart.replaceChildren();
  els.chartLegend.replaceChildren();
}

function hideRangeEnhancements() {
  hideCalorieChart();
  els.weekSummaryPanel.classList.add("hidden");
  els.weekSummary.replaceChildren();
  els.dayComparePanel.classList.add("hidden");
  els.rangeDashboard.replaceChildren();
}

function renderRangeEnhancements() {
  renderRangeDashboard();
  renderWeekSummary();
  populateDayComparison();
  renderCalorieChart();
}

function renderRangeDashboard() {
  const days = state.rangeDays;
  els.rangeDashboard.replaceChildren();
  if (days.length === 0) {
    return;
  }
  const energies = days.map((day) => Number(day.total?.energy ?? 0));
  const proteins = days.map((day) => Number(day.total?.protein ?? 0));
  const goals = days.map((day) => Number(day.daily?.energyGoal ?? 0)).filter((value) => value > 0);
  const maxDay = days.reduce((best, day) => Number(day.total?.energy ?? 0) > Number(best.total?.energy ?? 0) ? day : best, days[0]);
  const minDay = days.reduce((best, day) => Number(day.total?.energy ?? 0) < Number(best.total?.energy ?? 0) ? day : best, days[0]);
  const goalHits = days.filter((day) => Number(day.daily?.energyGoal ?? 0) > 0 && Number(day.total?.energy ?? 0) <= Number(day.daily?.energyGoal ?? 0)).length;
  const cards = [
    ["Tage", days.length],
    ["Ø kcal", `${fmt(avg(energies))} kcal`],
    ["Ø Protein", `${fmt(avg(proteins))} g`],
    ["Ziel eingehalten", goals.length ? `${goalHits}/${goals.length}` : "-"],
    ["Höchster Tag", `${shortDate(maxDay.date)} · ${fmt(maxDay.total.energy)} kcal`],
    ["Niedrigster Tag", `${shortDate(minDay.date)} · ${fmt(minDay.total.energy)} kcal`]
  ];
  els.rangeDashboard.append(...cards.map(([label, value]) => statCard(label, value)));
}

function renderWeekSummary() {
  const days = state.rangeDays;
  els.weekSummaryPanel.classList.toggle("hidden", days.length < 7);
  els.weekSummary.replaceChildren();
  if (days.length < 7) {
    return;
  }
  const groups = new Map();
  for (const day of days) {
    const key = weekKey(day.date);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(day);
  }
  const table = insightTable(["Woche", "Tage", "Gesamt kcal", "Ø kcal", "Ø Protein", "Ø Fett"]);
  const body = table.querySelector("tbody");
  for (const [key, weekDays] of groups.entries()) {
    const total = sumMacros(weekDays);
    const row = document.createElement("tr");
    row.append(
      td(key),
      td(String(weekDays.length), "number-cell"),
      td(`${fmt(total.energy)} kcal`, "number-cell"),
      td(`${fmt(total.energy / weekDays.length)} kcal`, "number-cell"),
      td(`${fmt(total.protein / weekDays.length)} g`, "number-cell"),
      td(`${fmt(total.fat / weekDays.length)} g`, "number-cell")
    );
    body.append(row);
  }
  els.weekSummary.append(table);
}

function populateDayComparison() {
  const days = state.rangeDays;
  els.dayComparePanel.classList.toggle("hidden", days.length < 2);
  if (days.length < 2) {
    return;
  }
  const options = days.map((day) => {
    const option = document.createElement("option");
    option.value = day.date;
    option.textContent = formatDate(day.date);
    return option;
  });
  els.compareDayA.replaceChildren(...options.map((option) => option.cloneNode(true)));
  els.compareDayB.replaceChildren(...options.map((option) => option.cloneNode(true)));
  els.compareDayA.value = days[0].date;
  els.compareDayB.value = days[days.length - 1].date;
  renderDayComparison();
}

function renderDayComparison() {
  const dayA = state.rangeDays.find((day) => day.date === els.compareDayA.value);
  const dayB = state.rangeDays.find((day) => day.date === els.compareDayB.value);
  els.compareResult.replaceChildren();
  if (!dayA || !dayB) {
    return;
  }
  const rows = [
    ["Kalorien", dayA.total.energy, dayB.total.energy, "kcal"],
    ["Protein", dayA.total.protein, dayB.total.protein, "g"],
    ["KH", dayA.total.carbs, dayB.total.carbs, "g"],
    ["Fett", dayA.total.fat, dayB.total.fat, "g"]
  ];
  const table = insightTable(["Makro", "Tag A", "Tag B", "Differenz"]);
  const body = table.querySelector("tbody");
  for (const [label, a, b, unit] of rows) {
    const diff = Number(b ?? 0) - Number(a ?? 0);
    const row = document.createElement("tr");
    row.append(
      td(label),
      td(`${fmt(a)} ${unit}`, "number-cell"),
      td(`${fmt(b)} ${unit}`, "number-cell"),
      td(`${diff >= 0 ? "+" : ""}${fmt(diff)} ${unit}`, "number-cell")
    );
    body.append(row);
  }
  els.compareResult.append(table);
}

function renderCalorieChart() {
  const days = state.rangeDays;
  els.toggleCalorieChart.textContent = state.chartVisible ? "Graph ausblenden" : "Graph anzeigen";
  els.calorieChartPanel.classList.toggle("hidden", !state.chartVisible || days.length < 2);
  els.calorieChart.replaceChildren();
  if (!state.chartVisible || days.length < 2) {
    return;
  }

  const metric = chartMetrics[state.chartMetric] ?? chartMetrics.energy;
  els.chartMetric.value = state.chartMetric;
  els.chartTitle.textContent = metric.title;
  const width = 920;
  const height = 330;
  const margin = { top: 22, right: 24, bottom: 54, left: 110 };
  const plotWidth = width - margin.left - margin.right;
  const plotHeight = height - margin.top - margin.bottom;
  const values = days.map(metric.value);
  const goals = metric.goal ? days.map(metric.goal).filter((value) => value > 0) : [];
  const maxValue = Math.max(100, ...values, ...goals);
  const stepBase = metric.unit === "kcal" ? 250 : 25;
  const yMax = Math.ceil((maxValue * 1.1) / stepBase) * stepBase;
  const average = avg(values);
  const svg = svgNode("svg", {
    viewBox: `0 0 ${width} ${height}`,
    role: "img",
    "aria-label": "Kalorienverlauf im gewählten Datumsbereich"
  });

  for (let i = 0; i <= 4; i++) {
    const value = Math.round((yMax / 4) * i);
    const y = margin.top + plotHeight - (value / yMax) * plotHeight;
    svg.append(
      svgNode("line", { x1: margin.left, y1: y, x2: width - margin.right, y2: y, class: "chart-grid" }),
      svgText(margin.left - 10, y + 4, `${fmt(value)} ${metric.unit}`, "chart-axis-label chart-y-label")
    );
  }

  svg.append(
    svgNode("line", { x1: margin.left, y1: margin.top, x2: margin.left, y2: margin.top + plotHeight, class: "chart-axis" }),
    svgNode("line", { x1: margin.left, y1: margin.top + plotHeight, x2: width - margin.right, y2: margin.top + plotHeight, class: "chart-axis" })
  );

  const pointFor = (day, index) => {
    const value = metric.value(day);
    const x = margin.left + (days.length === 1 ? plotWidth / 2 : (plotWidth / (days.length - 1)) * index);
    const y = margin.top + plotHeight - (value / yMax) * plotHeight;
    return { x, y, value };
  };
  const points = days.map(pointFor);
  const avgY = margin.top + plotHeight - (average / yMax) * plotHeight;
  svg.append(svgNode("line", { x1: margin.left, y1: avgY, x2: width - margin.right, y2: avgY, class: "chart-average-line" }));

  const legendItems = [
    ["chart-average-line", `Durchschnitt: ${fmt(average)} ${metric.unit}`]
  ];
  if (metric.goal && goals.length > 0) {
    legendItems.push(["chart-goal-line", `Ziel: Ø ${fmt(avg(goals))} ${metric.unit}`]);
    const goalPoints = days
      .map((day, index) => ({ value: metric.goal(day), index }))
      .filter((point) => point.value > 0)
      .map((point) => {
        const x = margin.left + (plotWidth / (days.length - 1)) * point.index;
        const y = margin.top + plotHeight - (point.value / yMax) * plotHeight;
        return `${x},${y}`;
      });
    if (goalPoints.length > 1) {
      svg.append(svgNode("polyline", { points: goalPoints.join(" "), class: "chart-goal-line" }));
    }
  }
  renderChartLegend(legendItems);

  svg.append(svgNode("polyline", {
    points: points.map((point) => `${point.x},${point.y}`).join(" "),
    class: "chart-line"
  }));

  const labelStep = Math.max(1, Math.ceil(days.length / 10));
  days.forEach((day, index) => {
    const point = points[index];
    const group = svgNode("g", { class: "chart-point", tabindex: "0", role: "button" });
    group.append(svgNode("title", {}, `${formatDate(day.date)}: ${fmt(point.value)} ${metric.unit} - Tag in neuem Tab öffnen`));
    group.append(svgNode("circle", { cx: point.x, cy: point.y, r: 5 }));
    group.addEventListener("click", () => openDay(day.date));
    group.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        openDay(day.date);
      }
    });
    svg.append(group);

    if (index % labelStep === 0 || index === days.length - 1) {
      svg.append(svgText(point.x, margin.top + plotHeight + 24, shortDate(day.date), "chart-axis-label chart-x-label"));
    }
  });

  els.calorieChart.append(svg);
}

function renderChartLegend(items) {
  els.chartLegend.replaceChildren(...items.map(([lineClass, label]) => {
    const item = document.createElement("span");
    item.className = "chart-legend-item";
    const sample = document.createElement("i");
    sample.className = lineClass;
    item.append(sample, document.createTextNode(label));
    return item;
  }));
}

function svgNode(name, attributes = {}, text = null) {
  const node = document.createElementNS("http://www.w3.org/2000/svg", name);
  for (const [key, value] of Object.entries(attributes)) {
    node.setAttribute(key, String(value));
  }
  if (text != null) {
    node.textContent = text;
  }
  return node;
}

function svgText(x, y, text, className) {
  return svgNode("text", { x, y, class: className }, text);
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
  const days = payload.days ?? [];
  const summary = document.createElement("div");
  summary.className = "dashboard-grid product-history-summary";
  const total = days.reduce((sum, day) => {
    sum.energy += Number(day.macro?.energy ?? 0);
    sum.protein += Number(day.macro?.protein ?? 0);
    sum.count += Number(day.count ?? 0);
    return sum;
  }, { energy: 0, protein: 0, count: 0 });
  summary.append(
    statCard("Verzehrtage", days.length),
    statCard("Einträge", total.count),
    statCard("Gesamt kcal", `${fmt(total.energy)} kcal`),
    statCard("Gesamt Protein", `${fmt(total.protein)} g`)
  );
  const table = insightTable(["Tag", "Menge", "Kalorien", "Protein", "Einträge", ""]);
  const body = table.querySelector("tbody");
  for (const day of days) {
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
  els.productDayResults.replaceChildren(summary, table);
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

async function loadClassificationRules() {
  const payload = await readJson(await fetch("/api/item-classifications"));
  renderClassificationRules(payload.items ?? []);
}

function renderClassificationRules(items) {
  els.classificationRules.replaceChildren();
  if (items.length === 0) {
    els.classificationRules.textContent = "Keine gelernten Produktregeln vorhanden.";
    return;
  }
  const table = insightTable(["Lebensmittel", "Zuordnung", ""]);
  const body = table.querySelector("tbody");
  for (const item of items) {
    const row = document.createElement("tr");
    row.append(
      td(item.producer ? `${item.name} · ${item.producer}` : item.name),
      td(item.classification === "drink" ? "getrunken" : "gegessen"),
      actionTd("Löschen", async () => {
        const payload = await readJson(await fetch("/api/item-classifications", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ key: item.key })
        }));
        renderClassificationRules(payload.items ?? []);
        showMessage("Produktregel gelöscht.", "ok");
      })
    );
    body.append(row);
  }
  els.classificationRules.append(table);
}

async function loadDataQuality() {
  const payload = await readJson(await fetch("/api/data-quality"));
  const items = payload.items ?? [];
  els.dataQualityResults.replaceChildren();
  if (items.length === 0) {
    els.dataQualityResults.textContent = "Keine Auffälligkeiten gefunden.";
    return;
  }
  const table = insightTable(["Stufe", "Tag", "Typ", "Hinweis", ""]);
  const body = table.querySelector("tbody");
  for (const item of items) {
    const row = document.createElement("tr");
    row.append(
      td(item.severity),
      td(formatDate(item.date)),
      td(item.type),
      td(item.message),
      actionTd("Öffnen", () => openDay(item.date))
    );
    body.append(row);
  }
  els.dataQualityResults.append(table);
}

async function loadUsers() {
  const payload = await readJson(await fetch("/api/users"));
  renderUsers(payload.items ?? []);
}

function renderUsers(items) {
  els.userResults.replaceChildren();
  if (els.adminPasswordWarning) {
    els.adminPasswordWarning.classList.toggle("hidden", !state.auth?.adminPasswordDefault);
  }
  const table = insightTable(["ID", "Benutzername", "Name", "Rolle", "Status", "Datenordner", "Angelegt", "Letzter Login", "Aktionen"]);
  const body = table.querySelector("tbody");
  for (const user of items) {
    const row = document.createElement("tr");
    row.append(
      td(user.id),
      td(user.username),
      td(user.name ?? ""),
      td(user.demo ? "Demo" : (user.admin ? "Admin" : "User")),
      td(user.active ? "Aktiv" : "Deaktiviert"),
      td(user.dataDir ?? ""),
      td(formatDateTime(user.createdAt)),
      td(formatDateTime(user.lastLogin)),
      userActionsTd(user)
    );
    body.append(row);
  }
  els.userResults.append(table);
}

function userActionsTd(user) {
  const cell = document.createElement("td");
  if (user.admin || user.demo) {
    cell.textContent = "-";
    return cell;
  }
  const activeButton = document.createElement("button");
  activeButton.type = "button";
  activeButton.className = "secondary";
  activeButton.textContent = user.active ? "Deaktivieren" : "Aktivieren";
  activeButton.addEventListener("click", () => updateUserActive(user.id, !user.active));
  const deleteButton = document.createElement("button");
  deleteButton.type = "button";
  deleteButton.className = "secondary danger";
  deleteButton.textContent = "Löschen";
  deleteButton.addEventListener("click", () => deleteUser(user.id, user.username));
  cell.append(activeButton, deleteButton);
  return cell;
}

async function updateUserActive(id, active) {
  const payload = await readJson(await fetch("/api/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "active", id, active })
  }));
  renderUsers(payload.items ?? []);
  showMessage(active ? "Benutzer aktiviert." : "Benutzer deaktiviert.", "ok");
}

async function deleteUser(id, username) {
  if (!window.confirm(`Benutzer ${username} wirklich löschen? Der Datenordner wird ebenfalls entfernt.`)) {
    return;
  }
  const payload = await readJson(await fetch("/api/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "delete", id })
  }));
  renderUsers(payload.items ?? []);
  showMessage("Benutzer und Datenordner wurden gelöscht.", "ok");
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
  const learnProduct = Boolean(item.productId && !item.aiGenerated && item.serving !== "simple_product")
    && window.confirm("Diese Zuordnung künftig für dieses Produkt merken?");
  const response = await fetch("/api/item-classification", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      itemId: item.itemId,
      classification: select.value,
      learnProduct
    })
  });
  const payload = await readJson(response);
  item.classification = payload.classification;
  item.automaticClassification = payload.automaticClassification;
  item.classificationOverridden = payload.classificationOverridden;
  select.classList.toggle("overridden", Boolean(payload.classificationOverridden));
  showMessage(payload.learnedProduct
    ? "Zuordnung für dieses Produkt gespeichert."
    : payload.classificationOverridden ? "Zuordnung gespeichert." : "Zuordnung auf Automatik zurückgesetzt.", "ok");
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
    if (!validRange(els.fromDate.value, els.toDate.value)) {
      showMessage("Das Startdatum darf nicht nach dem Enddatum liegen.");
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

function initializeRangeDates(status) {
  if (!state.rangeInitialized) {
    applyDefaultRangeDates(status);
    state.rangeInitialized = true;
    state.rangeDefaultPending = true;
    window.requestAnimationFrame(() => reapplyInitialRangeDates(status));
    window.setTimeout(() => reapplyInitialRangeDates(status), 150);
    window.setTimeout(() => reapplyInitialRangeDates(status), 500);
    return;
  }
  if (!els.fromDate.value) els.fromDate.value = defaultRangeFrom(status);
  if (!els.toDate.value) els.toDate.value = defaultRangeTo(status);
}

function applyDefaultRangeDates(status) {
  els.fromDate.value = defaultRangeFrom(status);
  els.toDate.value = defaultRangeTo(status);
}

function reapplyInitialRangeDates(status) {
  if (!state.rangeDefaultPending) {
    return;
  }
  applyDefaultRangeDates(status);
  updateRangeConstraints();
}

function cancelPendingRangeDefault() {
  state.rangeDefaultPending = false;
}

function daysAgoIso(days) {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return toIsoDate(date);
}

function defaultRangeFrom(status) {
  if (status.defaultRangeFrom) {
    return status.defaultRangeFrom;
  }
  let start = daysAgoIso(7);
  if (status.firstDate && start < status.firstDate) {
    start = status.firstDate;
  }
  if (status.lastDate && start > status.lastDate) {
    start = status.lastDate;
  }
  return start;
}

function defaultRangeTo(status) {
  return status.defaultRangeTo ?? status.lastDate ?? todayIso();
}

function parseIsoDate(value) {
  return value ? new Date(`${value}T12:00:00`) : null;
}

function toIsoDate(date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
}

function validRange(from, to) {
  const start = parseIsoDate(from);
  const end = parseIsoDate(to);
  return Boolean(start && end && start <= end);
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
  if (pageName === "users") {
    loadUsers().catch((error) => showMessage(error.message));
  }
}

function closeMenu() {
  els.mainNav.classList.remove("open");
  els.menuToggle.setAttribute("aria-expanded", "false");
  els.menuToggle.setAttribute("aria-label", "Menü öffnen");
}

async function initAuth() {
  const auth = await readJson(await fetch("/api/auth/status"));
  state.auth = auth;
  if (auth.userManagement && !auth.loggedIn) {
    showLogin();
    return;
  }
  await showAuthenticatedApp();
}

async function showAuthenticatedApp() {
  els.loginScreen.classList.add("hidden");
  els.appShell.classList.remove("hidden");
  const user = state.auth?.user;
  els.currentUser.textContent = user ? `${user.username} (${user.id})` : "admin (1337)";
  els.logoutButton.classList.toggle("hidden", !state.auth?.userManagement);
  els.usersNav.classList.toggle("hidden", !(state.auth?.userManagement && user?.admin));
  els.passwordPanel.classList.toggle("hidden", !state.auth?.userManagement);
  els.passwordForm.classList.toggle("hidden", Boolean(user?.admin || user?.demo));
  els.passwordHint.textContent = user?.admin
    ? "Das Admin-Passwort wird ueber YAZIO_ADMIN_PASSWORD oder yazio.admin.password verwaltet."
    : user?.demo
    ? "Der Demo-Benutzer hat das feste Passwort Demo und speichert keine echten Daten."
    : "Hier aenderst du das Passwort fuer deinen lokalen Benutzer.";
  if (els.adminPasswordWarning) {
    els.adminPasswordWarning.classList.toggle("hidden", !state.auth?.adminPasswordDefault);
  }
  await loadStatus();
  await openDateFromUrl();
}

function showLogin(message = "") {
  els.appShell.classList.add("hidden");
  els.loginScreen.classList.remove("hidden");
  els.loginPassword.value = "";
  els.loginMessage.textContent = message;
}

async function readJson(response) {
  const payload = await response.json();
  if (!response.ok) {
    if (response.status === 401 && state.auth?.userManagement) {
      showLogin();
    }
    throw new Error(payload.error ?? `HTTP ${response.status}`);
  }
  return payload;
}

function formatDate(value) {
  if (!value) return "";
  return new Intl.DateTimeFormat("de-DE", {
    weekday: "short",
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(new Date(`${value}T12:00:00`));
}

function formatDateTime(value) {
  if (!value) return "";
  const normalized = String(value).includes("T") ? value : String(value).replace(" ", "T");
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function shortDate(value) {
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit"
  }).format(new Date(`${value}T12:00:00`));
}

function statCard(label, value) {
  const node = document.createElement("div");
  node.className = "stat-card";
  const title = document.createElement("span");
  title.textContent = label;
  const number = document.createElement("strong");
  number.textContent = value;
  node.append(title, number);
  return node;
}

function avg(values) {
  const usable = values.filter((value) => Number.isFinite(value));
  return usable.length ? usable.reduce((sum, value) => sum + value, 0) / usable.length : 0;
}

function sumMacros(days) {
  return days.reduce((sum, day) => {
    sum.energy += Number(day.total?.energy ?? 0);
    sum.carbs += Number(day.total?.carbs ?? 0);
    sum.protein += Number(day.total?.protein ?? 0);
    sum.fat += Number(day.total?.fat ?? 0);
    return sum;
  }, { energy: 0, carbs: 0, protein: 0, fat: 0 });
}

function weekKey(value) {
  const date = parseIsoDate(value);
  const day = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - day);
  const monday = toIsoDate(date);
  date.setDate(date.getDate() + 6);
  return `${shortDate(monday)} bis ${shortDate(toIsoDate(date))}`;
}

function fmt(value) {
  const rounded = Number(value ?? 0);
  return new Intl.NumberFormat("de-DE", {
    maximumFractionDigits: Math.abs(rounded - Math.round(rounded)) < 0.01 ? 0 : 1
  }).format(rounded);
}

restoreInsightSelection();
initAuth().catch((error) => {
  showLogin();
  els.loginMessage.textContent = error.message;
});
