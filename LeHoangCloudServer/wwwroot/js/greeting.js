/* Greetingbot is isolated from the existing Luckibot fleet protocol. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  let adminKey = '', selectedId = '', current = null, loading = false, settingsDirty = false;
  let actionPending = false, selectionVersion = 0;
  let lanMode = false, accessChecked = false, connecting = false, listing = false;
  let inventoryKey = '';
  const labels = {queued:'Chờ robot nhận', delivered:'Đã gửi, chờ xác nhận', unconfirmed:'Chưa nhận xác nhận', running:'Đang thực hiện', completed:'Hoàn tất', failed:'Thất bại', expired:'Đã hết hạn', idle:'Sẵn sàng', moving:'Đang di chuyển', checking:'Đang kiểm tra', stopping:'Đang chờ dừng', wait_door:'Chờ mở cửa cabin', wait_floor:'Chờ báo tầng', positioning:'Đang định vị', floor_unknown:'Cần xác nhận tầng và vị trí', recording:'Đang ghi âm', playing:'Đang phát lại'};
  const types = {navigate:'Đi tới điểm', stop_navigation:'Dừng điều hướng', refresh_places:'Đọc lại điểm', microphone_test:'Test microphone và loa'};
  function notice(message, error = false) { $('greetingNotice').textContent = message; $('greetingNotice').classList.toggle('error', error); }
  async function api(path, options = {}) {
    const response = await fetch('/api/greeting' + path, {...options, cache:'no-store', headers:{'Content-Type':'application/json', 'X-Greeting-Admin-Key':adminKey}});
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || Object.values(data.errors || {}).flat().join(' ') || `HTTP ${response.status}`);
    return data;
  }
  function ready() { return !!(current?.online && current.snapshot.ready && !actionPending); }
  function maps(snapshot) {
    const result = new Map((snapshot?.maps || []).map(map => [map.name, map.places]));
    if (snapshot?.mapName) result.set(snapshot.mapName, snapshot.places);
    return result;
  }
  function renderElevator() {
    const state = current?.snapshot, config = current?.elevator;
    $('greetingEditElevator').disabled = !current;
    const summary = !current ? 'Chọn robot để cấu hình.' : config?.floors.length
      ? config.floors.map(floor => `Tầng ${floor.number} · ${floor.mapName}${floor.number === config.mainChargingFloor ? ' (sạc chính)' : ''}`).join(' / ')
      : 'Chưa cấu hình các tầng hoạt động.';
    $('greetingElevatorSummary').textContent = summary + (state?.mapInventoryMessage ? ' ' + state.mapInventoryMessage : '');
    const catalog = [...maps(state)];
    const key = JSON.stringify([selectedId, catalog]);
    if (inventoryKey === key) return;
    inventoryKey = key;
    const list = $('greetingMapInventory');
    const expanded = new Set([...list.querySelectorAll('details[open]')].map(node => node.dataset.map));
    list.replaceChildren();
    for (const [name, points] of catalog) {
      const details = document.createElement('details'), heading = document.createElement('summary');
      details.dataset.map = name;
      details.open = expanded.has(name);
      heading.textContent = `${name} · ${points.length} điểm`;
      const names = document.createElement('p');
      names.textContent = points.map(point => point.name + (point.status === 0 ? '' : ' (không thể tới)')).join(' · ') || 'Chưa đọc được điểm.';
      details.append(heading, names);
      list.append(details);
    }
  }
  function editElevator() {
    if (!current) return;
    const id = current.id, catalog = maps(current.snapshot);
    const original = current.elevator || {floors:[], mainChargingFloor:null};
    const dialog = $('greetingElevatorDialog'), form = $('greetingElevatorForm'), rows = $('greetingFloorRows');
    const count = $('greetingFloorCount'), mainFloor = $('greetingMainChargingFloor'), status = $('greetingElevatorNotice');
    const save = $('greetingSaveElevator');
    const pointFields = [['waitingPoint','Điểm chờ thang'], ['cabinPoint','Điểm trong cabin'], ['chargingPoint','Điểm sạc trên map']];
    function options(select, values, selected, placeholder) {
      select.replaceChildren(new Option(placeholder, ''));
      for (const value of values) select.add(new Option(value, value));
      if (selected != null && selected !== '' && !values.includes(String(selected))) {
        const missing = new Option(selected + ' · chưa có trên robot', selected);
        missing.disabled = true;
        select.add(missing);
      }
      select.value = selected ?? '';
    }
    function readRows() {
      return [...rows.children].map(row => Object.fromEntries(
        [...row.querySelectorAll('input,select')].map(field => [field.name, field.value])));
    }
    function updateMain() {
      const selected = mainFloor.value;
      const numbers = [...new Set(readRows().map(floor => floor.number).filter(number => number !== ''))];
      options(mainFloor, numbers, numbers.includes(selected) ? selected : '', 'Chọn tầng sạc chính');
      mainFloor.required = mainFloor.disabled = false;
      if (rows.children.length === 0) mainFloor.disabled = true;
      else mainFloor.required = true;
    }
    function field(row, title, name, element) {
      const label = document.createElement('label');
      label.textContent = title;
      element.name = name;
      element.required = true;
      label.append(element);
      row.append(label);
      return element;
    }
    function renderRows(floors) {
      rows.replaceChildren();
      for (let index = 0; index < count.valueAsNumber; index++) {
        const data = floors[index] || {number:index + 1};
        const row = document.createElement('div');
        row.className = 'greeting-floor-row';
        const number = field(row, 'Tầng', 'number', document.createElement('input'));
        Object.assign(number, {type:'number', min:-999, max:999, step:1, value:data.number});
        number.oninput = updateMain;
        const map = field(row, 'Bản đồ', 'mapName', document.createElement('select'));
        options(map, [...catalog.keys()], data.mapName, 'Chọn bản đồ');
        const points = pointFields.map(([name, title]) => field(row, title, name, document.createElement('select')));
        function fillPoints(previous) {
          const names = [...new Set((catalog.get(map.value) || []).filter(point => point.status === 0).map(point => point.name))];
          for (const point of points) options(point, names, previous[point.name], 'Chọn điểm');
        }
        map.onchange = () => fillPoints({});
        fillPoints(data);
        rows.append(row);
      }
      updateMain();
    }
    count.value = original.floors.length;
    status.textContent = catalog.size ? '' : 'Chưa có dữ liệu bản đồ. Mở Greetingbot và đọc lại điểm.';
    renderRows(original.floors);
    mainFloor.value = original.mainChargingFloor ?? '';
    count.oninput = () => { if (count.validity.valid) renderRows(readRows()); };
    form.onsubmit = async event => {
      event.preventDefault();
      if (save.disabled) return;
      save.disabled = true;
      status.textContent = '';
      const floors = readRows().map(floor => ({...floor, number:Number(floor.number)}));
      try {
        await api('/robots/' + encodeURIComponent(id) + '/elevator', {method:'PUT', body:JSON.stringify({
          floors, mainChargingFloor:floors.length ? Number(mainFloor.value) : null
        })});
        dialog.close();
        notice('Đã lưu cấu hình thang máy. Robot sẽ nhận khi đồng bộ.');
        await loadDetail();
      } catch (error) { status.textContent = error.message; }
      finally { save.disabled = false; }
    };
    dialog.showModal();
  }
  function render() {
    const state = current?.snapshot;
    $('greetingOnline').textContent = current?.online ? 'Robot trực tuyến' : 'Robot ngoại tuyến';
    $('greetingOnline').classList.toggle('online', !!current?.online);
    $('greetingRobotName').textContent = current ? current.name + (lanMode ? '' : ' · ' + current.id) : 'Điểm đến trên robot';
    $('greetingMap').textContent = (state?.mapName || 'Chưa đọc được') +
      (state?.currentFloor != null ? ' · Tầng ' + state.currentFloor : '') +
      (state?.elevatorFloor != null ? ' · Cabin báo tầng ' + state.elevatorFloor : '');
    renderElevator();
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
    if (!selectedId || loading || (!adminKey && !lanMode)) return;
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
    if (listing) return;
    listing = true;
    try {
    const robots = await api('/robots');
    const select = $('greetingRobotSelect'); select.replaceChildren();
    for (const robot of robots) { const option = document.createElement('option'); option.value = robot.id; option.textContent = robot.name + (lanMode ? '' : ' · ' + robot.id); select.append(option); }
    const nextId = robots.some(r => r.id === preferredId) ? preferredId : robots[0]?.id || '';
    if (selectedId !== nextId) { selectedId = nextId; selectionVersion++; current = null; settingsDirty = false; render(); }
    select.value = selectedId;
    if (selectedId) await loadDetail();
    else notice(lanMode ? 'Chờ robot tự kết nối. Mở app Greetingbot khi robot cùng mạng LAN với server.' : 'Chưa có Greetingbot. Tạo mã ghép nối ở bên dưới.');
    } finally { listing = false; }
  }
  async function connectAutomatically() {
    if (connecting) return;
    connecting = true;
    try {
      const access = await api('/access');
      lanMode = access.managementMode === 'lan'; accessChecked = true;
      $('greetingAdminField').hidden = lanMode; $('greetingAccessButton').hidden = lanMode;
      $('greetingAdminKey').required = !lanMode; $('greetingRegisterCard').hidden = lanMode;
      if (lanMode) { await loadRobots(selectedId); if (selectedId) notice('Đã tự kết nối trong LAN. Chọn robot để điều khiển hoặc chỉnh cấu hình.'); }
      else notice('Nhập mã quản trị để tải danh sách Greetingbot.');
    } catch (error) { notice(error.message, true); }
    finally { connecting = false; }
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
  $('greetingEditElevator').addEventListener('click', editElevator);
  $('greetingCancelElevator').addEventListener('click', () => $('greetingElevatorDialog').close());
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
  async function refreshPanel() {
    try {
      if (!accessChecked) await connectAutomatically();
      else if (lanMode) await loadRobots(selectedId);
      else if (adminKey) await loadDetail();
    } catch (error) { current = null; render(); notice(error.message, true); }
  }
  window.GreetingPanel = {open:refreshPanel};
  setInterval(() => { if ($('viewGreeting').style.display !== 'none') refreshPanel(); }, 2000);
  render();
  connectAutomatically();
})();
