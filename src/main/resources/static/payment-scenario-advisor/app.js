const scenarioList = document.querySelector("#scenarioList");
const recommendation = document.querySelector("#recommendation");
const simulationPlan = document.querySelector("#simulationPlan");
const feedbackReport = document.querySelector("#feedbackReport");

async function loadScenarios() {
  const response = await fetch("/v1/payment-scenario-advisor/scenarios");
  const catalog = await response.json();
  renderScenarioList(catalog.scenarios);
  if (catalog.scenarios.length > 0) {
    await selectScenario(catalog.scenarios[0].scenarioId);
  }
}

function renderScenarioList(scenarios) {
  scenarioList.innerHTML = "";
  scenarios.forEach((scenario) => {
    const button = document.createElement("button");
    button.className = "scenario-button";
    button.type = "button";
    button.dataset.scenarioId = scenario.scenarioId;
    button.innerHTML = `<strong>${scenario.businessLabel}</strong><small>${scenario.businessDescription}</small>`;
    button.addEventListener("click", () => selectScenario(scenario.scenarioId));
    scenarioList.appendChild(button);
  });
}

async function selectScenario(scenarioId) {
  document.querySelectorAll(".scenario-button").forEach((button) => {
    button.classList.toggle("active", button.dataset.scenarioId === scenarioId);
  });

  const response = await fetch(`/v1/payment-scenario-advisor/scenarios/${scenarioId}`);
  const scenario = await response.json();
  renderRecommendation(scenario);
  renderSimulationPlan(scenario);
  renderFeedback(scenario);
}

function renderRecommendation(scenario) {
  recommendation.innerHTML = `
    <div class="metric-grid">
      ${metric("Recommended rail", scenario.recommendation.rail)}
      ${metric("Arrangement", scenario.recommendation.arrangement)}
      ${metric("Confidence", scenario.recommendation.confidenceLevel)}
      ${metric("Reason", scenario.recommendation.reasonCode)}
    </div>
    <p class="note">${scenario.recommendation.summary}</p>
  `;
}

function renderSimulationPlan(scenario) {
  const plan = scenario.simulationPlan;
  simulationPlan.innerHTML = `
    <div class="metric-grid">
      ${metric("API", `${plan.method} ${plan.endpoint}`)}
      ${metric("Payload", plan.payloadFormat)}
      ${metric("Mock scenario", plan.mockScenario)}
      ${metric("Expected status", plan.expectedStatus)}
    </div>
    <p class="note">User confirmation is required. This is simulator-only guidance, not production routing.</p>
  `;
}

function renderFeedback(scenario) {
  const report = scenario.feedbackReport;
  feedbackReport.innerHTML = `
    <div class="metric-grid">
      ${metric("Validation", report.validationStatus)}
      ${metric("Outcome", report.expectedOutcome)}
    </div>
    <p>${report.businessConclusion}</p>
    <span class="tag">${report.nextStep}</span>
  `;
}

function metric(label, value) {
  return `<div class="metric"><span>${label}</span><strong>${value}</strong></div>`;
}

loadScenarios().catch(() => {
  scenarioList.innerHTML = "<p>Advisor scenarios are unavailable.</p>";
});
