/* Greetingbot is isolated from the existing Luckibot fleet protocol. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  let adminKey = '', selectedId = '', current = null, loading = false, settingsDirty = false;
  let actionPending = false, selectionVersion = 0;
  const labels = {queued:'Chờ robot nhận', delivered:'Đã gửi, chờ xác nhận', unconfirmed:'Chưa nhận xác nhận', running:'Đang thực hiện', completed:'Hoàn tất', failed:'Thất bại', expired:'Đã hết hạn', idle:'Sẵn sàng', moving:'Đang di chuyển', checking:'Đang kiểm tra', stopping:'Đang chờ dừng', recording:'Đang ghi âm', playing:'Đang phát lại'};
  const types = {navigate:'Đi tới điểm', stop_navigation:'Dừng điều hướng', refresh_places:'Đọc lại điểm', microphone_test:'Test microphone và loa'};
  function notice(message, error = false) { $('greetingNotice').textContent = message; $('greetingNotice').classList.toggle('error', error); }
  async function api(path, options = {}) {
    const response = await fetch('/api/greeting' + path, {...options, cache:'no-store', headers:{'Content-Type':'application/json', 'X-Greeting-Admin-Key':adminKey}});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || Object.values(data.errors || {}).flat().join(' ') || `HTTP ${response.status}`);
    return data;
  }
  function ready() { return !!(current?.online && current.snapshot.ready && !actionPending); }
  function render() {
    const state = current?.snapshot;
    $('greetingOnline').textContent = current?.online ? 'Robot trực tuyến' : 'Robot ngoại tuyến';
    $('greetingOnline').classList.toggle('online', !!current?.online);
    $('greetingRobotName').textContent = current ? current.name + ' · ' + current.id : 'Điểm đến trên robot';
    $('greetingMap').textContent = state?.mapName || 'Chưa đọc được';
    $('greetingSdk').textContent = !state ? '—' : !state.sdkConnected ? 'SDK chưa kết nối' : !state.authorized ? 'Cần mở app từ Home RobotOS' : state.suspended ? 'RobotOS đang tiếp quản' : !state.foreground ? 'App đang ở nền' : state.localized ? 'Đã định vị · Sẵn sàng' : 'Chưa định vị';
    $('greetingNavigation').textContent = state ? (labels[state.navigationState] || state.navigationState) + (state.target ? ' · ' + state.target : '') : '—';
    $('greetingMicrophone').textContent = labels[state?.microphoneState] || '—';
    $('greetingRobotMessage').textContent = state?.message || 'Mở app Greetingbot từ Home của RobotOS.';
    $('greetingRefresh').disabled = !ready();
    $('greetingStop').disabled = !ready();
    $('greetingMic').disabled = !ready() || state.microphoneState !== 'idle';
    $('greetingSettings').querySelector('button').disabled = !current;
    const points = $('greetingPlaces'); points.replaceChildren();
    if (!state?.places?.length) { const text = document.createElement('p'); text.textContent = 'Chưa có điểm từ Map Tool của robot.'; points.append(text); }
    for (const point of state?.places || []) {
      const button = document.createElement('button'); button.type = 'button';
      button.textContent = point.name + (point.status === 0 ? ' →' : ' · Không thể tới');
      button.disabled = !ready() || !state.localized || state.navigationState !== 'idle' || point.status !== 0;
      const map = state.mapName;
      button.addEventListener('click', () => send('navigate', point.name, map)); points.append(button);
    }
    const history = $('greetingCommands'); history.replaceChildren();
    for (const command of current?.commands || []) {
      const item = document.createElement('li'), detail = document.createElement('small');
      item.textContent = `${types[command.type] || command.type}${command.placeName ? ' · ' + command.placeName : ''} — ${labels[command.status] || command.status}`;
      detail.textContent = new Date(command.createdAt).toLocaleTimeString('vi-VN') + ' · ' + (command.message || '');
      item.append(detail); history.append(item);
    }
    if (!history.children.length) { const item = document.createElement('li'); item.textContent = 'Chưa có lệnh.'; history.append(item); }
  }
  async function loadDetail() {
    if (!selectedId || loading || !adminKey) return;
    loading = true;
    const id = selectedId, version = selectionVersion;
    try {
      const robot = await api('/robots/' + encodeURIComponent(id));
      if (version !== selectionVersion) return;
      current = robot;
      if (!settingsDirty) for (const [key, value] of Object.entries(robot.settings)) {
        const input = $('greetingSettings').elements.namedItem(key); if (input) input.value = value;
      }
      render();
    } catch (error) { if (version === selectionVersion) { current = null; render(); notice(error.message, true); } }
    finally { loading = false; }
  }
  async function loadRobots(preferredId) {
    const robots = await api('/robots');
    const select = $('greetingRobotSelect'); select.replaceChildren();
    for (const robot of robots) { const option = document.createElement('option'); option.value = robot.id; option.textContent = robot.name + ' · ' + robot.id; select.append(option); }
    selectedId = robots.some(r => r.id === preferredId) ? preferredId : robots[0]?.id || '';
    select.value = selectedId; selectionVersion++; current = null; settingsDirty = false; render();
    if (selectedId) await loadDetail();
    else notice('Chưa có Greetingbot. Tạo mã ghép nối ở bên dưới.');
  }
  async function send(type, placeName = '', mapName = '') {
    if (!ready()) return;
    actionPending = true; render();
    try {
      await api('/robots/' + encodeURIComponent(selectedId) + '/commands', {method:'POST', body:JSON.stringify({type, placeName, mapName})});
      notice('Đã gửi lệnh. Theo dõi xác nhận thực tế từ SDK trong Kết quả lệnh gần đây.');
      await loadDetail();
    } catch (error) { notice(error.message, true); }
    finally { actionPending = false; render(); }
  }
  $('greetingAccess').addEventListener('submit', async event => {
    event.preventDefault(); adminKey = $('greetingAdminKey').value.trim();
    try { await loadRobots(selectedId); if (selectedId) notice('Đã kết nối quản lý Greetingbot.'); }
    catch (error) { current = null; render(); notice(error.message, true); }
  });
  $('greetingRobotSelect').addEventListener('change', () => { selectedId = $('greetingRobotSelect').value; selectionVersion++; settingsDirty = false; current = null; render(); loadDetail(); });
  $('greetingRefresh').addEventListener('click', () => send('refresh_places'));
  $('greetingStop').addEventListener('click', () => send('stop_navigation'));
  $('greetingMic').addEventListener('click', () => send('microphone_test'));
  $('greetingSettings').addEventListener('input', () => { settingsDirty = true; });
  $('greetingSettings').addEventListener('submit', async event => {
    event.preventDefault(); if (!selectedId) return;
    const id = selectedId, settings = Object.fromEntries([...new FormData(event.target)].map(([key, value]) => [key, Number(value)]));
    try { await api('/robots/' + encodeURIComponent(id) + '/settings', {method:'PUT', body:JSON.stringify(settings)}); settingsDirty = false; notice('Đã lưu. Robot nhận cấu hình khi đồng bộ tiếp theo.'); }
    catch (error) { notice(error.message, true); }
  });
  $('greetingRegister').addEventListener('submit', async event => {
    event.preventDefault(); if (!adminKey) { notice('Kết nối bằng mã quản trị trước khi tạo robot.', true); return; }
    try {
      const result = await api('/robots', {method:'POST', body:JSON.stringify(Object.fromEntries(new FormData(event.target)))});
      $('greetingPairServer').value = location.origin; $('greetingPairId').value = result.robot.id; $('greetingPairToken').value = result.deviceToken;
      $('greetingPairing').showModal(); await loadRobots(result.robot.id);
      notice('Đã tạo Greetingbot. Nhập mã ghép nối trong Cấu hình kết nối Web trên robot.');
    } catch (error) { notice(error.message, true); }
  });
  $('greetingClosePairing').addEventListener('click', () => { $('greetingPairing').close(); $('greetingPairToken').value = ''; });
  $('greetingPairing').addEventListener('close', () => { $('greetingPairToken').value = ''; });
  window.GreetingPanel = {open() { if (adminKey) loadDetail(); }};
  setInterval(() => { if ($('viewGreeting').style.display !== 'none') loadDetail(); }, 2000);
  render();
})();
