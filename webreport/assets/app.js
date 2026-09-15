/* Renders the TC catalog and the latest verification. Every number comes from the JSON. */

const $ = (id) => document.getElementById(id);
const text = (el, s) => { el.textContent = s; return el; };

// A run is only ever as good as what actually reported. PASSED is the one state that
// claims everything; the rest say what is missing rather than implying success.
const RUN_COPY = {
  PASSED: ["is-ok", "Passed", (s) => `all ${s.coverage.expected} suites reported and passed`],
  FAILED: ["is-bad", "Failed", (s) => `${s.counts.byState.failed || 0} test cases failed`],
  BLOCKED: ["is-warn", "Blocked", () => "a suite failed before its tests could run, or ran none at all"],
  INCOMPLETE: ["is-warn", "Incomplete", (s) =>
    `only ${s.coverage.reported} of ${s.coverage.expected} suites reported — this is not a pass`],
  CANCELLED: ["is-warn", "Cancelled", () => "the run was cancelled before it finished"],
  NO_DATA: ["", "No results", () => "nothing was collected for this commit"],
};

const TC_LABEL = {
  passed: "passed",
  failed: "failed",
  skipped: "skipped",
  blocked: "blocked",
  "not-selected": "not run by its job",
  "no-report": "job did not report",
  "not-in-ci": "not run by CI",
};

let ROWS = [];
let REPO = "";

const load = (name) => fetch(`data/${name}.json`).then((r) => (r.ok ? r.json() : null)).catch(() => null);

function repoFrom(entries) {
  const url = entries.find((e) => e.sourceUrl)?.sourceUrl || "";
  const m = url.match(/github\.com\/([^/]+\/[^/]+)\/blob\//);
  return m ? m[1] : "";
}

function renderBanner(status) {
  const el = $("banner");
  if (!status) {
    el.hidden = false;
    el.innerHTML = '<div class="headline">No verification published yet</div>';
    return;
  }
  const [cls, head, detail] = RUN_COPY[status.state] || ["", status.state, () => ""];
  el.className = `banner ${cls}`;
  el.hidden = false;
  el.innerHTML = "";
  const h = document.createElement("div");
  h.className = "headline";
  h.textContent = head;
  el.append(h);
  const d = document.createElement("div");
  d.className = "detail";
  d.textContent = detail(status);
  el.append(d);
}

function renderCounts(defined, status) {
  const el = $("counts");
  const executed = status ? status.counts.executed : 0;
  const cells = [
    ["Defined", defined, "test cases in the code"],
    ["Executed", executed, "including one run per parameterized case"],
  ];
  if (status) {
    for (const k of ["passed", "failed", "skipped", "blocked"]) {
      if (status.counts.byState[k]) cells.push([k[0].toUpperCase() + k.slice(1), status.counts.byState[k], ""]);
    }
  }
  el.innerHTML = "";
  for (const [label, value, note] of cells) {
    const div = document.createElement("div");
    div.innerHTML = `<dt>${label}</dt><dd>${value.toLocaleString()}</dd>` +
      (note ? `<div class="note">${note}</div>` : "");
    el.append(div);
  }
}

function renderRun(status) {
  const meta = $("run-meta");
  if (!status) { meta.textContent = "No run collected yet."; return; }
  const sha = status.commit.slice(0, 7);
  meta.innerHTML =
    `Commit <b><a href="https://github.com/${REPO}/commit/${status.commit}"><code>${sha}</code></a></b> · ` +
    `run <b><a href="${status.run.url}">#${status.run.id}</a></b> attempt ${status.run.attempt} · ` +
    `started ${new Date(status.run.startedAt).toLocaleString()}`;

  const body = $("suites");
  body.innerHTML = "";
  for (const s of status.suites) {
    const tr = document.createElement("tr");
    const c = s.counts;
    tr.innerHTML =
      `<td><code>${s.name}</code></td>` +
      `<td><span class="state ${s.state}">${s.state}</span></td>` +
      `<td class="num">${c.passed}</td><td class="num">${c.failed}</td>` +
      `<td class="num">${c.skipped}</td><td class="num">${c.blocked}</td>`;
    body.append(tr);
  }
}

function fillSelect(el, values) {
  for (const v of values) {
    const o = document.createElement("option");
    o.value = o.textContent = v;
    el.append(o);
  }
}

function apply() {
  const q = $("q").value.trim().toLowerCase();
  const type = $("f-type").value;
  const group = $("f-group").value;
  const ci = $("f-ci").value;
  const state = $("f-state").value;

  const hits = ROWS.filter((r) => {
    if (type && r.type !== type) return false;
    if (group && r.group[0] !== group) return false;
    if (ci && (ci === "yes") !== !!r.ciExecuted) return false;
    if (state && r.state !== state) return false;
    if (!q) return true;
    return r.haystack.includes(q);
  });

  const url = new URL(location.href);
  for (const [k, v] of [["q", q], ["type", type], ["group", group], ["ci", ci], ["state", state]]) {
    if (v) url.searchParams.set(k, v); else url.searchParams.delete(k);
  }
  history.replaceState(null, "", url);

  text($("shown"), `${hits.length.toLocaleString()} of ${ROWS.length.toLocaleString()} test cases`);

  const body = $("rows");
  body.innerHTML = "";
  const frag = document.createDocumentFragment();
  for (const r of hits.slice(0, 400)) {
    const tr = document.createElement("tr");
    tr.innerHTML =
      `<td><span class="state ${r.state}">${TC_LABEL[r.state] || r.state}</span></td>` +
      `<td><span class="tc-name"></span><span class="tc-method"></span></td>` +
      `<td class="group"></td>` +
      `<td><span class="pill">${r.type}</span>${r.kind === "parameterized" ? ' <span class="pill">parameterized</span>' : ""}</td>` +
      `<td>${r.ciExecuted ? `<code>${r.ciJob || "yes"}</code>` : '<span class="state not-in-ci">no</span>'}</td>` +
      `<td><a href="${r.sourceUrl}">source</a></td>`;
    tr.querySelector(".tc-name").textContent = r.name;
    tr.querySelector(".tc-method").textContent = `${r.class.split(".").pop()}#${r.method}`;
    tr.querySelector(".group").textContent = r.group.join(" › ");
    frag.append(tr);
  }
  body.append(frag);
  if (hits.length > 400) {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td colspan="6" class="group">showing the first 400 — narrow the filters to see the rest</td>`;
    body.append(tr);
  }
}

// The only thing that keeps "never show a stale green" true when publishing itself fails.
async function checkStale(status) {
  if (!status || !REPO) return;
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/commits/develop`);
    if (!r.ok) return;
    const head = (await r.json()).sha;
    if (head && head !== status.commit) {
      const el = $("stale");
      el.hidden = false;
      el.textContent =
        `develop has moved to ${head.slice(0, 7)}. What you see below verifies ${status.commit.slice(0, 7)}, ` +
        `so the newest commit has not been published yet.`;
    }
  } catch { /* offline or rate limited: the page is still correct about what it did publish */ }
}

async function main() {
  const [unit, e2e, status] = await Promise.all([load("catalog-unit"), load("catalog-e2e"), load("status")]);
  const entries = [...(unit?.entries || []), ...(e2e?.entries || [])];
  REPO = repoFrom(entries);

  const byTest = status?.byTest || {};
  ROWS = entries.map((e) => ({
    ...e,
    state: byTest[e.id] || (e.ciExecuted ? "no-report" : "not-in-ci"),
    haystack: [e.name, e.group.join(" "), e.class, e.method].join(" ").toLowerCase(),
  }));
  ROWS.sort((a, b) => a.group.join("/").localeCompare(b.group.join("/")) || a.name.localeCompare(b.name));

  renderBanner(status);
  renderCounts(entries.length, status);
  renderRun(status);
  checkStale(status);

  fillSelect($("f-type"), [...new Set(ROWS.map((r) => r.type))].sort());
  fillSelect($("f-group"), [...new Set(ROWS.map((r) => r.group[0]).filter(Boolean))].sort());
  fillSelect($("f-state"), [...new Set(ROWS.map((r) => r.state))].sort());

  const p = new URL(location.href).searchParams;
  $("q").value = p.get("q") || "";
  for (const [id, key] of [["f-type", "type"], ["f-group", "group"], ["f-ci", "ci"], ["f-state", "state"]]) {
    if (p.get(key)) $(id).value = p.get(key);
  }
  for (const id of ["q", "f-type", "f-group", "f-ci", "f-state"]) {
    $(id).addEventListener("input", apply);
  }
  apply();

  if (REPO) {
    $("dispatch-link").href = `https://github.com/${REPO}/actions/workflows/ci.yml`;
    $("foot").innerHTML =
      `Generated from the test code on <code>develop</code>. ` +
      `<a href="https://github.com/${REPO}/blob/develop/tests/README.md">How test cases are written</a>.`;
  }
}

main();
