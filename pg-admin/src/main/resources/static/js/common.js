const nowNodes = document.querySelectorAll(".js-now");

const formatTime = () => {
  const now = new Date();
  return now.toLocaleString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "numeric",
  });
};

const updateTime = () => {
  const label = formatTime();
  nowNodes.forEach((node) => {
    node.textContent = label;
  });
};

if (nowNodes.length > 0) {
  updateTime();
  window.setInterval(updateTime, 60000);
}
