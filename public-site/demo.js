// const API_BASE = "http://127.0.0.1:8000";
const API_BASE = "https://quietsignal.noxbound.com";
const SUBMIT_FRAME = "quietsignal-submit-frame";
const PHOTO_PATH = "/photo";
const VOICE_PATH = "/voice";
const PHOTO_ASSET = "sadie.png";
const VOICE_ASSET = "sadie.mp3";
const PHOTO_FILENAME = "photos/2/sadie.png";
const VOICE_FILENAME = "2/sadie.mp3";

// Tree-stage images live in imgs/ (1.png, 2.png, …). Each "Grow tree" click
// sends the next one to Norman, which waters the shared tree by one stage on
// both the elderly and adult screens. There are 10 stage images.
const GROW_IMG_COUNT = 10;
let growStep = 1; // 1-based index of the next stage image to send.

const statusEl = document.getElementById("demo-status");
const photoButton = document.getElementById("send-photo-btn");
const voiceButton = document.getElementById("send-voice-btn");
const growButton = document.getElementById("grow-tree-btn");

function setStatus(message, tone = "idle") {
  if (!statusEl) {
    return;
  }

  statusEl.textContent = message;
  statusEl.dataset.state = tone;
}

function setButtonsDisabled(disabled) {
  if (photoButton) {
    photoButton.disabled = disabled;
  }
  if (voiceButton) {
    voiceButton.disabled = disabled;
  }
  if (growButton) {
    growButton.disabled = disabled;
  }
}

function submitFile(path, file) {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = `${API_BASE}${path}`;
  form.enctype = "multipart/form-data";
  form.target = SUBMIT_FRAME;
  form.style.display = "none";

  const input = document.createElement("input");
  input.type = "file";
  input.name = "file";

  const transfer = new DataTransfer();
  transfer.items.add(file);
  input.files = transfer.files;

  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
  setTimeout(() => form.remove(), 0);
}

async function createPlaceholderPhoto() {
  return loadLocalAsset(PHOTO_ASSET, PHOTO_FILENAME, "image/png");
}

async function createPlaceholderVoice() {
  return loadLocalAsset(VOICE_ASSET, VOICE_FILENAME, "audio/mpeg");
}

async function loadLocalAsset(assetPath, uploadName, mimeType) {
  const response = await fetch(new URL(assetPath, document.baseURI));
  if (!response.ok) {
    throw new Error(`Could not load ${assetPath}`);
  }

  const bytes = await response.arrayBuffer();
  return new File([bytes], uploadName, { type: mimeType });
}

async function sendDemoMedia(path, fileFactory, successMessage) {
  setButtonsDisabled(true);
  setStatus("Sending demo media…", "sending");

  try {
    const file = await fileFactory();
    submitFile(path, file);
    setStatus(successMessage, "done");
  } catch (error) {
    console.error(error);
    setStatus("Could not send the demo media.", "error");
  } finally {
    setButtonsDisabled(false);
  }
}

// Send the next stage image (imgs/<n>.png) to Norman, watering the shared tree
// by one stage. Uploaded under "photos/2/..." so it counts as Sadie watering.
function createStagePhoto(n) {
  return loadLocalAsset(`imgs/${n}.png`, `photos/2/${n}.png`, "image/png");
}

if (growButton) {
  growButton.addEventListener("click", () => {
    const n = growStep;
    growStep = (growStep % GROW_IMG_COUNT) + 1; // cycle 1..10
    void sendDemoMedia(
      PHOTO_PATH,
      () => createStagePhoto(n),
      `Grew the tree — sent stage image ${n}.png to Norman.`,
    );
  });
}

if (photoButton) {
  photoButton.addEventListener("click", () => {
    void sendDemoMedia(PHOTO_PATH, createPlaceholderPhoto, "Submitted Sadie's placeholder picture to Norman.");
  });
}

if (voiceButton) {
  voiceButton.addEventListener("click", () => {
    void sendDemoMedia(VOICE_PATH, () => Promise.resolve(createPlaceholderVoice()), "Submitted Sadie's placeholder voice note to Norman.");
  });
}
