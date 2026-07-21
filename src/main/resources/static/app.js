(() => {
  "use strict";

  const views = {
    upload: document.getElementById("upload-view"),
    loading: document.getElementById("loading-view"),
    report: document.getElementById("report-view"),
  };

  const els = {
    newCaseBtn: document.getElementById("new-case-btn"),
    reportNewBtn: document.getElementById("report-new-btn"),
    caseList: document.getElementById("case-list"),
    sampleBtn: document.getElementById("sample-btn"),
    analyzeBtn: document.getElementById("analyze-btn"),
    uploadForm: document.getElementById("upload-form"),
    caseTitle: document.getElementById("case-title"),
    docType: document.getElementById("doc-type"),
    contractText: document.getElementById("contract-text"),
    stepFacts: document.getElementById("step-facts"),
    stepLaw: document.getElementById("step-law"),
    stepRisk: document.getElementById("step-risk"),
    reportTitle: document.getElementById("report-title"),
    riskGauge: document.getElementById("risk-gauge"),
    riskLevel: document.getElementById("risk-level"),
    totalTime: document.getElementById("total-time"),
    timingChips: document.getElementById("timing-chips"),
    partiesCard: document.getElementById("parties-card"),
    financialCard: document.getElementById("financial-card"),
    missingCard: document.getElementById("missing-card"),
    lawsTable: document.getElementById("laws-table"),
    approvalsList: document.getElementById("approvals-list"),
    riskCategories: document.getElementById("risk-categories"),
    topRisks: document.getElementById("top-risks"),
    mitigationList: document.getElementById("mitigation-list"),
  };

  let stepTimers = [];
  let activeCaseId = null;

  const SAMPLE_CONTRACT = `MERGER AGREEMENT

This Merger Agreement ("Agreement") is entered into as of January 15, 2026, by and between:

PARTY A: TechCorp Inc., a Delaware corporation ("Acquirer")
PARTY B: InnovateSoft Ltd., an Indian private limited company ("Target")

1. TRANSACTION
Acquirer agrees to acquire 100% of Target's outstanding shares for a total purchase price of $500,000,000 USD.

2. PURCHASE PRICE
The total purchase price shall be $500,000,000 USD, payable at closing.
An additional earnout of $50,000,000 shall be payable if Target achieves $100,000,000 in revenue for fiscal year 2027.

3. CLOSING
The closing of this transaction shall occur on June 30, 2026, subject to customary closing conditions.

4. GOVERNING LAW
This Agreement shall be governed by the laws of the State of Delaware, without regard to conflict of law principles.

5. TERMINATION
Either party may terminate this Agreement under certain conditions.
Notice of termination must be provided at least 15 days prior to the effective date of termination.

6. AMENDMENT
Acquirer may amend the terms of this Agreement upon written notice to Target.

7. KEY EMPLOYEES
Target shall use reasonable efforts to retain key employees through the closing date.

8. REPRESENTATIONS
Each party represents that it has authority to enter into this Agreement.

Intentionally omitted: indemnity, force majeure, and dispute resolution clauses.`;

  function esc(str) {
    if (str == null) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function showView(name) {
    Object.entries(views).forEach(([key, el]) => {
      if (!el) return;
      const visible = key === name;
      el.classList.toggle("hidden", !visible);
      el.classList.toggle("view-hidden", !visible);
      if (visible) {
        el.removeAttribute("hidden");
      } else {
        el.setAttribute("hidden", "");
      }
    });
  }

  function clearForm() {
    if (els.caseTitle) els.caseTitle.value = "";
    if (els.docType) els.docType.value = "MERGER_AGREEMENT";
    if (els.contractText) els.contractText.value = "";
  }

  function clearActiveCaseItems() {
    document.querySelectorAll(".case-item.active").forEach((item) => {
      item.classList.remove("active");
    });
    activeCaseId = null;
  }

  function goToUpload() {
    clearTimers();
    clearForm();
    clearActiveCaseItems();
    showView("upload");
  }

  function formatCaseDate(iso) {
    if (!iso) return "—";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "—";
    const date = d.toLocaleDateString("en-GB", {
      day: "numeric",
      month: "short",
    });
    const time = d.toLocaleTimeString("en-GB", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
    return `${date}, ${time}`;
  }

  function cardBody(card) {
    if (!card) return null;
    return card.querySelector(".card-body") || card;
  }

  function setStep(el, state) {
    if (!el) return;
    el.classList.remove(
      "pending",
      "active",
      "done",
      "step-pending",
      "step-active",
      "step-done"
    );
    el.classList.add(state, `step-${state}`);

    const statusEl = el.querySelector(".step-status");
    if (!statusEl) return;

    if (state === "pending") statusEl.textContent = "waiting";
    else if (state === "active") statusEl.textContent = "running…";
    else if (state === "done") statusEl.textContent = "✓ done";
  }

  function clearTimers() {
    stepTimers.forEach((id) => clearTimeout(id));
    stepTimers = [];
  }

  function startStepAnimation() {
    clearTimers();
    setStep(els.stepFacts, "pending");
    setStep(els.stepLaw, "pending");
    setStep(els.stepRisk, "pending");
  }

  function applyStatusToSteps(status) {
    const s = String(status || "").toUpperCase();
    if (s === "EXTRACTING" || s === "CREATED" || s === "STARTED") {
      setStep(els.stepFacts, "active");
      setStep(els.stepLaw, "pending");
      setStep(els.stepRisk, "pending");
      return;
    }
    if (s === "ANALYZING") {
      setStep(els.stepFacts, "done");
      setStep(els.stepLaw, "active");
      setStep(els.stepRisk, "pending");
      return;
    }
    if (s === "REVIEWING") {
      setStep(els.stepFacts, "done");
      setStep(els.stepLaw, "done");
      setStep(els.stepRisk, "active");
      return;
    }
    if (s === "COMPLETED") {
      markAllStepsDone();
    }
  }

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async function pollAnalysisStatus(caseId) {
    const maxAttempts = 120;
    for (let i = 0; i < maxAttempts; i++) {
      const res = await fetch(`/api/cases/${encodeURIComponent(caseId)}/status`);
      if (!res.ok) {
        const errBody = await res.json().catch(() => ({}));
        throw new Error(errBody.message || `Status poll failed (${res.status})`);
      }
      const data = await res.json();
      applyStatusToSteps(data.status);

      if (data.status === "COMPLETED") return data;
      if (data.status === "FAILED") {
        throw new Error("Analysis failed");
      }

      await sleep(2000);
    }
    throw new Error("Analysis timed out while waiting for agents");
  }

  function markAllStepsDone() {
    setStep(els.stepFacts, "done");
    setStep(els.stepLaw, "done");
    setStep(els.stepRisk, "done");
  }

  function severityColor(level) {
    const key = String(level || "").toUpperCase();
    if (key === "LOW") return "#34d399";
    if (key === "MEDIUM") return "#fbbf24";
    if (key === "HIGH") return "#fb923c";
    if (key === "CRITICAL") return "#f87171";
    return "#6366f1";
  }

  function severityClass(level) {
    return `sev-${String(level || "medium").toLowerCase()}`;
  }

  async function loadCaseHistory() {
    if (!els.caseList) return;
    try {
      const res = await fetch("/api/cases");
      if (!res.ok) throw new Error(`Failed to load cases (${res.status})`);
      const cases = await res.json();
      renderCaseList(Array.isArray(cases) ? cases : []);
    } catch (err) {
      console.error(err);
      els.caseList.innerHTML =
        '<p class="case-item-meta">Unable to load case history</p>';
    }
  }

  function renderCaseList(cases) {
    if (!els.caseList) return;
    if (!cases.length) {
      els.caseList.innerHTML =
        '<p class="case-item-meta">No cases yet</p>';
      return;
    }

    els.caseList.innerHTML = cases
      .map((c) => {
        const status = String(c?.status || "CREATED").toLowerCase();
        const isActive = c?.id && c.id === activeCaseId ? " active" : "";
        return `
          <div class="case-item${isActive}" data-id="${esc(c.id)}" data-status="${esc(c.status)}" data-title="${esc(c.title || "Untitled")}">
            <div class="case-item-title">${esc(c.title || "Untitled")}</div>
            <div class="case-item-meta">
              <span class="badge badge-${esc(status)}">${esc(c.status || "CREATED")}</span>
              <span>${esc(formatCaseDate(c.createdAt))}</span>
            </div>
          </div>`;
      })
      .join("");
  }

  async function onCaseItemClick(event) {
    const item = event.target.closest(".case-item");
    if (!item || !els.caseList.contains(item)) return;

    const caseId = item.dataset.id;
    const status = String(item.dataset.status || "").toUpperCase();
    const title = item.dataset.title || "Analysis Report";

    if (status === "FAILED") {
      alert("This analysis failed.");
      return;
    }

    if (status !== "COMPLETED") {
      alert("Analysis not completed for this case.");
      return;
    }

    try {
      const res = await fetch(`/api/cases/${encodeURIComponent(caseId)}/report`);
      if (res.status === 404) {
        alert(
          "Report not available — analysis data is cleared on server restart. Please re-run analysis."
        );
        return;
      }
      if (!res.ok) throw new Error(`Failed to load report (${res.status})`);
      const report = await res.json();
      activeCaseId = caseId;
      document.querySelectorAll(".case-item").forEach((el) => {
        el.classList.toggle("active", el.dataset.id === caseId);
      });
      renderReport(report, title);
      showView("report");
    } catch (err) {
      if (String(err.message || "").includes("404")) {
        alert(
          "Report not available — analysis data is cleared on server restart. Please re-run analysis."
        );
        return;
      }
      alert("Failed to load report: " + (err.message || "Unknown error"));
    }
  }

  function loadSampleContract() {
    if (els.caseTitle) els.caseTitle.value = "TechCorp M&A Review";
    if (els.docType) els.docType.value = "MERGER_AGREEMENT";
    if (els.contractText) els.contractText.value = SAMPLE_CONTRACT;
  }

  async function runAnalysis(event) {
    event.preventDefault();

    const title = (els.caseTitle?.value || "").trim();
    const documentType = els.docType?.value || "OTHER";
    const documentText = (els.contractText?.value || "").trim();

    if (!title || !documentText) {
      alert("Please provide both a case title and contract text.");
      return;
    }

    if (els.analyzeBtn) els.analyzeBtn.disabled = true;
    showView("loading");
    startStepAnimation();
    applyStatusToSteps("EXTRACTING");

    try {
      const createRes = await fetch("/api/cases", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title, documentType, documentText }),
      });

      if (!createRes.ok) {
        const errBody = await createRes.json().catch(() => ({}));
        throw new Error(errBody.message || `Create failed (${createRes.status})`);
      }

      const created = await createRes.json();
      const caseId = created.id;
      activeCaseId = caseId;

      const analyzeRes = await fetch(
        `/api/cases/${encodeURIComponent(caseId)}/analyze`,
        { method: "POST" }
      );

      if (!analyzeRes.ok) {
        const errBody = await analyzeRes.json().catch(() => ({}));
        throw new Error(errBody.message || `Analysis failed (${analyzeRes.status})`);
      }

      await pollAnalysisStatus(caseId);

      const reportRes = await fetch(
        `/api/cases/${encodeURIComponent(caseId)}/report`
      );
      if (!reportRes.ok) {
        const errBody = await reportRes.json().catch(() => ({}));
        throw new Error(errBody.message || `Report fetch failed (${reportRes.status})`);
      }
      const report = await reportRes.json();

      clearTimers();
      markAllStepsDone();

      await sleep(400);

      renderReport(report, title || created.title || "Analysis Report");
      showView("report");
      await loadCaseHistory();
    } catch (err) {
      clearTimers();
      alert("Analysis failed: " + (err.message || "Unknown error"));
      showView("upload");
      await loadCaseHistory();
    } finally {
      if (els.analyzeBtn) els.analyzeBtn.disabled = false;
    }
  }

  function renderReport(report, title) {
    const facts = report?.facts || {};
    const laws = report?.laws || {};
    const risk = report?.riskAnalysis || {};
    const timings = report?.agentTimings || null;

    if (els.reportTitle) {
      els.reportTitle.textContent = title || "Analysis Report";
    }

    // Risk gauge (conic-gradient)
    const score = Number(risk.overallScore ?? 0);
    const level = String(risk.riskLevel || "MEDIUM").toUpperCase();
    const color = severityColor(level);
    const degrees = Math.max(0, Math.min(100, score)) * 3.6;

    if (els.riskGauge) {
      els.riskGauge.style.background = `conic-gradient(${color} 0deg, ${color} ${degrees}deg, #2a2f3c ${degrees}deg)`;
      els.riskGauge.innerHTML = `
        <div class="gauge-inner">
          <span class="gauge-score">${esc(score)}</span>
          <span class="gauge-suffix">/100</span>
        </div>`;
    }

    if (els.riskLevel) {
      els.riskLevel.textContent = level;
      els.riskLevel.className = `badge level-${level.toLowerCase()}`;
    }

    if (els.totalTime) {
      if (report?.totalProcessingTimeMs != null) {
        const seconds = (Number(report.totalProcessingTimeMs) / 1000).toFixed(1);
        els.totalTime.textContent = `Total analysis time: ${seconds}s`;
        els.totalTime.classList.remove("hidden");
        els.totalTime.style.display = "";
      } else {
        els.totalTime.textContent = "";
        els.totalTime.classList.add("hidden");
      }
    }

    if (els.timingChips) {
      if (timings) {
        const chips = [
          ["Facts Agent", timings.factsAgentMs],
          ["Law Agent", timings.lawAgentMs],
          ["Risk Agent", timings.riskAgentMs],
        ];
        els.timingChips.innerHTML = chips
          .map(([label, ms]) => {
            if (ms == null) return "";
            return `<span class="chip"><span class="chip-label">${esc(label)}</span><span class="chip-value">${esc(ms)}ms</span></span>`;
          })
          .join("");
      } else {
        els.timingChips.innerHTML = "";
      }
    }

    // Parties
    const partiesBody = cardBody(els.partiesCard);
    if (partiesBody) {
      const parties = facts.parties || [];
      if (!parties.length) {
        partiesBody.innerHTML = "<p>—</p>";
      } else {
        partiesBody.innerHTML = parties
          .map(
            (p) => `
            <div class="party-row">
              <span>${esc(p?.name || "—")}</span>
              <span class="role">${esc(p?.role || "—")}</span>
            </div>`
          )
          .join("");
      }
    }

    // Financial
    const financialBody = cardBody(els.financialCard);
    if (financialBody) {
      const ft = facts.financialTerms || {};
      const value = [ft.totalValue, ft.currency].filter(Boolean).join(" ") || "—";
      const schedule = ft.paymentSchedule || "—";
      financialBody.innerHTML = `
        <div class="party-row"><span>Total Value</span><span>${esc(value)}</span></div>
        <div class="party-row"><span>Payment Schedule</span><span class="role">${esc(schedule)}</span></div>`;
    }

    // Missing clauses
    const missingBody = cardBody(els.missingCard);
    if (missingBody) {
      const missing = facts.missingStandardClauses || [];
      if (!missing.length) {
        missingBody.innerHTML =
          '<p style="color: var(--green);">No missing standard clauses detected</p>';
      } else {
        missingBody.innerHTML = missing
          .map((m) => `<span class="missing-tag">${esc(m)}</span>`)
          .join("");
      }
    }

    // Laws table
    if (els.lawsTable) {
      const acts = laws.applicableActs || [];
      if (!acts.length) {
        els.lawsTable.innerHTML = "<p>—</p>";
      } else {
        els.lawsTable.innerHTML = `
          <table class="laws-table">
            <thead>
              <tr><th>Act</th><th>Sections</th><th>Relevance</th></tr>
            </thead>
            <tbody>
              ${acts
                .map((act) => {
                  const sections = act?.sections || [];
                  const chips = sections.length
                    ? sections
                        .map((s) => `<span class="section-pill">${esc(s)}</span>`)
                        .join("")
                    : "—";
                  return `<tr>
                    <td>${esc(act?.name || "—")}</td>
                    <td>${chips}</td>
                    <td>${esc(act?.relevance || "—")}</td>
                  </tr>`;
                })
                .join("")}
            </tbody>
          </table>`;
      }
    }

    // Approvals
    if (els.approvalsList) {
      const approvals = laws.regulatoryApprovals || [];
      if (!approvals.length) {
        els.approvalsList.innerHTML = "<p>—</p>";
      } else {
        els.approvalsList.innerHTML = approvals
          .map((a) => {
            const required = a?.required === true;
            return `
              <div class="approval-row">
                <div>
                  <strong>${esc(a?.body || "—")}</strong>
                  <span class="badge ${required ? "badge-completed" : "badge-created"}">${required ? "Required" : "Optional"}</span>
                  <div class="case-item-meta" style="margin-top:6px;">${esc(a?.timeline || "—")}</div>
                </div>
                <div class="penalty">${esc(a?.penalty || "")}</div>
              </div>`;
          })
          .join("");
      }
    }

    // Risk categories
    if (els.riskCategories) {
      const categories = risk.categories || [];
      if (!categories.length) {
        els.riskCategories.innerHTML = "<p>—</p>";
      } else {
        els.riskCategories.innerHTML = categories
          .map((cat) => {
            const sev = String(cat?.severity || "MEDIUM");
            const prob = Number(cat?.probability ?? 0);
            return `
              <div class="risk-cat">
                <div class="risk-cat-header">
                  <span>
                    <strong>${esc(cat?.name || "—")}</strong>
                    <span class="badge ${severityClass(sev)}">${esc(sev)}</span>
                  </span>
                  <span class="probability">${esc(prob)}% likely</span>
                </div>
                <div class="bar-track">
                  <div class="bar-fill ${severityClass(sev)}" style="width:${Math.max(0, Math.min(100, prob))}%"></div>
                </div>
                <p class="case-item-meta" style="margin-top:6px;">${esc(cat?.impact || "")}</p>
              </div>`;
          })
          .join("");
      }
    }

    // Top risks
    if (els.topRisks) {
      const topRisks = risk.topRisks || [];
      if (!topRisks.length) {
        els.topRisks.innerHTML = "<p>—</p>";
      } else {
        els.topRisks.innerHTML = topRisks
          .map((r) => {
            const sev = String(r?.severity || "MEDIUM");
            const color = severityColor(sev);
            return `
              <div class="risk-flag ${severityClass(sev)}" style="border-left-color:${color}">
                <div class="risk-cat-header">
                  <strong>${esc(r?.type || "Risk")}</strong>
                  <span class="badge ${severityClass(sev)}">${esc(sev)}</span>
                </div>
                <p class="description">${esc(r?.description || "")}</p>
                ${
                  r?.suggestion
                    ? `<p style="font-size:12px;font-style:italic;color:var(--text-muted);margin-top:6px;">💡 ${esc(r.suggestion)}</p>`
                    : ""
                }
              </div>`;
          })
          .join("");
      }
    }

    // Mitigations
    if (els.mitigationList) {
      const strategies = risk.mitigationStrategies || [];
      if (!strategies.length) {
        els.mitigationList.innerHTML = "<p>—</p>";
      } else {
        els.mitigationList.innerHTML = `<ul>${strategies
          .map((s) => `<li>${esc(s)}</li>`)
          .join("")}</ul>`;
      }
    }
  }

  // Event bindings
  els.newCaseBtn?.addEventListener("click", goToUpload);
  els.reportNewBtn?.addEventListener("click", goToUpload);
  els.sampleBtn?.addEventListener("click", loadSampleContract);
  els.uploadForm?.addEventListener("submit", runAnalysis);
  els.analyzeBtn?.addEventListener("click", (e) => {
    if (els.uploadForm) return; // form submit handler will run
    runAnalysis(e);
  });
  els.caseList?.addEventListener("click", onCaseItemClick);

  // Init
  showView("upload");
  loadCaseHistory();
})();
