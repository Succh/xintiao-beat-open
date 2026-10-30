package com.xintiao.beat;

import android.util.Log;

import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.node.DataItem;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;
import com.xiaomi.xms.wearable.notify.NotifyApi;
import com.xiaomi.xms.wearable.service.OnServiceConnectionListener;
import com.xiaomi.xms.wearable.service.ServiceApi;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Talks to the user's own band/watch through Xiaomi's Mi Wear SDK (xms-wearable-lib 1.4),
 * which goes through the Mi Fitness (com.mi.health) / Xiaomi Wear app.
 * Every call is guarded: if Mi Fitness is missing or the SDK refuses, we fall back to notifications.
 */
final class WearBridge {
    private static final String TAG = "BeatWear";
    private final Hub hub;
    private NodeApi nodeApi;
    private MessageApi messageApi;
    private NotifyApi notifyApi;
    private AuthApi authApi;
    private ServiceApi serviceApi;

    private String nodeId;
    private String nodeName = "";
    private boolean appInstalled;
    private boolean wearing;
    private boolean listening;
    private boolean permitted;
    String lastError = "";

    WearBridge(Hub hub) { this.hub = hub; }

    boolean hasNode() { return nodeId != null; }
    boolean appInstalled() { return appInstalled; }
    boolean wearing() { return wearing; }
    boolean permitted() { return permitted; }
    String deviceName() { return nodeName == null ? "" : nodeName; }

    void init() {
        try {
            nodeApi = Wearable.getNodeApi(hub.ctx);
            messageApi = Wearable.getMessageApi(hub.ctx);
            notifyApi = Wearable.getNotifyApi(hub.ctx);
            authApi = Wearable.getAuthApi(hub.ctx);
            serviceApi = Wearable.getServiceApi(hub.ctx);
            serviceApi.registerServiceConnectionListener(new OnServiceConnectionListener() {
                @Override public void onServiceConnected() { hub.main.post(WearBridge.this::refresh); }
                @Override public void onServiceDisconnected() {
                    hub.main.post(() -> { nodeId = null; listening = false; hub.onWearChanged(); });
                }
            });
        } catch (Throwable t) {
            fail("小米运动健康未安装或版本过旧", t);
        }
        refresh();
    }

    /** Find the connected band/watch, check permissions + whether our band app is installed, start listening. */
    void refresh() {
        if (nodeApi == null) return;
        try {
            nodeApi.getConnectedNodes()
                .addOnSuccessListener(nodes -> hub.main.post(() -> onNodes(nodes)))
                .addOnFailureListener(e -> hub.main.post(() -> {
                    fail("找不到已连接的手环/手表", e);
                    nodeId = null;
                    hub.onWearChanged();
                }));
        } catch (Throwable t) {
            fail("读取设备失败", t);
        }
    }

    private void onNodes(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            nodeId = null;
            nodeName = "";
            lastError = "小米运动健康里没有已连接的设备";
            hub.onWearChanged();
            return;
        }
        Node n = nodes.get(0);
        boolean changedNode = !n.id.equals(nodeId);
        nodeId = n.id;
        nodeName = n.name == null ? "" : n.name;
        if (changedNode) listening = false;
        lastError = "";
        checkPermission();
        hub.onWearChanged();
    }

    private void checkPermission() {
        try {
            authApi.checkPermission(nodeId, Permission.DEVICE_MANAGER)
                .addOnSuccessListener(ok -> hub.main.post(() -> {
                    permitted = ok != null && ok;
                    if (permitted) afterPermission();
                    else { lastError = "需要在小米运动健康里授权"; hub.changed(); }
                }))
                .addOnFailureListener(e -> hub.main.post(() -> fail("授权检查失败", e)));
        } catch (Throwable t) {
            fail("授权检查失败", t);
        }
    }

    /** Pops Mi Fitness' own permission dialog. Call from a user tap. */
    void requestPermission() {
        if (nodeId == null) { refresh(); hub.toast("先在小米运动健康里连上手环"); return; }
        try {
            authApi.requestPermission(nodeId, Permission.DEVICE_MANAGER, Permission.NOTIFY)
                .addOnSuccessListener(granted -> hub.main.post(() -> {
                    permitted = granted != null && granted.length > 0;
                    hub.toast(permitted ? "已授权 ✓" : "没有授权");
                    if (permitted) afterPermission();
                }))
                .addOnFailureListener(e -> hub.main.post(() -> fail("授权失败", e)));
        } catch (Throwable t) {
            fail("授权失败", t);
        }
    }

    private void afterPermission() {
        try {
            nodeApi.isWearAppInstalled(nodeId)
                .addOnSuccessListener(inst -> hub.main.post(() -> {
                    appInstalled = inst != null && inst;
                    hub.onWearChanged();
                }))
                .addOnFailureListener(e -> hub.main.post(() -> { appInstalled = false; hub.onWearChanged(); }));
            nodeApi.query(nodeId, DataItem.ITEM_WEARING)
                .addOnSuccessListener(r -> hub.main.post(() -> {
                    boolean w = r != null && r.isWearing;
                    if (w != wearing) { wearing = w; hub.sendStatus(); hub.changed(); }
                }));
        } catch (Throwable t) {
            fail("读取设备状态失败", t);
        }
        listen();
    }

    private void listen() {
        if (listening || nodeId == null || messageApi == null) return;
        try {
            messageApi.addListener(nodeId, (id, bytes) -> {
                String s = new String(bytes, StandardCharsets.UTF_8);
                hub.main.post(() -> {
                    try { hub.onBandMessage(new JSONObject(s)); } catch (Exception e) { Log.w(TAG, "bad band msg " + s); }
                });
            }).addOnSuccessListener(v -> hub.main.post(() -> listening = true))
              .addOnFailureListener(e -> hub.main.post(() -> fail("监听手环消息失败", e)));
        } catch (Throwable t) {
            fail("监听手环消息失败", t);
        }
    }

    void sendToBand(JSONObject msg) {
        if (nodeId == null || messageApi == null || !permitted) return;
        try {
            messageApi.sendMessage(nodeId, msg.toString().getBytes(StandardCharsets.UTF_8))
                .addOnFailureListener(e -> Log.d(TAG, "sendMessage failed: " + e));
        } catch (Throwable t) {
            Log.d(TAG, "sendMessage threw: " + t);
        }
    }

    /**
     * Full-watch-app mode: open our band app (it may not be running), then hand it the event.
     * If that fails, use Xiaomi's watch notification, and finally a normal phone notification.
     */
    void deliver(JSONObject bandMsg, String title, String body) {
        if (nodeId == null || !permitted || !appInstalled) {
            notifyWatch(title, body);
            return;
        }
        try {
            nodeApi.launchWearApp(nodeId, "/pages/index")
                .addOnSuccessListener(v -> hub.main.postDelayed(hub::flushPendingToBand, 1500))
                .addOnFailureListener(e -> hub.main.post(() -> {
                    // app may already be in the foreground; try a direct message, then notify
                    hub.flushPendingToBand();
                    notifyWatch(title, body);
                }));
        } catch (Throwable t) {
            notifyWatch(title, body);
        }
    }

    private void notifyWatch(String title, String body) {
        if (nodeId == null || notifyApi == null) {
            hub.onBandDeliveryFailed(title, body);
            return;
        }
        try {
            notifyApi.sendNotify(nodeId, title, body)
                .addOnSuccessListener(st -> hub.main.post(() -> {
                    if (st == null || !st.isSuccess()) hub.onBandDeliveryFailed(title, body);
                }))
                .addOnFailureListener(e -> hub.main.post(() -> hub.onBandDeliveryFailed(title, body)));
        } catch (Throwable t) {
            hub.onBandDeliveryFailed(title, body);
        }
    }

    private void fail(String what, Throwable t) {
        lastError = what;
        Log.w(TAG, what + ": " + t);
        hub.changed();
    }
}
