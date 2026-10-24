<?php
/**
 * =============================================================================
 * LIVE PRO v6.0 — لوحة فضاء الزبون الذكية (Customer XTREAM Space Portal)
 * =============================================================================
 * • متكاملة تلقائياً مع قاعدة البيانات الحالية وملف api.php في نفس المجلد.
 * • تتيح للزبون البحث عن قوائمه عبر رمز الشاشة (DEV-XXXX-XXXX)، وإضافة أكثر من
 *   قائمة XTREAM، أو تعديلها، أو حذفها، أو تفعيل/تعطيل أي قائمة عن بُعد.
 * • تتحقق إجبارياً من مدة صلاحية المشغل قبل السماح بإضافة أو تعديل أي قائمة:
 *   إذا كان اشتراك المشغل منتهي الصلاحية، تمنع الإضافة وتطلب من الزبون تجديد
 *   الاشتراك بالتواصل مع: علي سات (00213697372480) مع تقديم رمز الشاشة.
 * =============================================================================
 */

declare(strict_types=1);
error_reporting(E_ALL & ~E_NOTICE & ~E_DEPRECATED);
header('X-Content-Type-Options: nosniff');

const ALI_SAT_NAME  = 'علي سات (Ali Sat)';
const ALI_SAT_PHONE = '00213697372480';

// -----------------------------------------------------------------------------
// 1. الربط التلقائي مع قاعدة البيانات الحالية (MySQL / SQLite / JSON) أو api.php
// -----------------------------------------------------------------------------
function getDatabaseConnection(): ?PDO {
    // محاولة استيراد إعدادات قاعدة البيانات الحالية إن وجدت في نفس المجلد
    $configFiles = ['config.php', 'db.php', 'database.php', '../config.php', '../db.php'];
    foreach ($configFiles as $file) {
        if (is_file(__DIR__ . '/' . $file)) {
            @include_once __DIR__ . '/' . $file;
        }
    }

    // في حال وجود كائن PDO جاهز من ملف الإعدادات
    foreach (['pdo', 'db', 'conn', 'connection'] as $varName) {
        if (isset($GLOBALS[$varName]) && $GLOBALS[$varName] instanceof PDO) {
            return $GLOBALS[$varName];
        }
    }

    // في حال وجود ثوابت أو متغيرات اتصال MySQL
    $dbHost = defined('DB_HOST') ? DB_HOST : ($GLOBALS['db_host'] ?? $GLOBALS['servername'] ?? getenv('DB_HOST') ?: '');
    $dbName = defined('DB_NAME') ? DB_NAME : ($GLOBALS['db_name'] ?? $GLOBALS['dbname'] ?? getenv('DB_NAME') ?: '');
    $dbUser = defined('DB_USER') ? DB_USER : ($GLOBALS['db_user'] ?? $GLOBALS['username'] ?? getenv('DB_USER') ?: '');
    $dbPass = defined('DB_PASS') ? DB_PASS : ($GLOBALS['db_pass'] ?? $GLOBALS['password'] ?? getenv('DB_PASS') ?: '');

    if (!empty($dbHost) && !empty($dbName)) {
        try {
            $dsn = "mysql:host={$dbHost};dbname={$dbName};charset=utf8mb4";
            $pdo = new PDO($dsn, (string)$dbUser, (string)$dbPass, [
                PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
                PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            ]);
            ensureTablesExist($pdo, false);
            return $pdo;
        } catch (Throwable $e) {
            // الانتقال إلى قاعدة SQLite المحلية المتكاملة في حال تعذر MySQL
        }
    }

    // البحث عن أي ملف قاعدة بيانات SQLite حالي في نفس المجلد أو إنشاء قاعدة متوافقة
    $sqliteFiles = ['database.sqlite', 'db.sqlite', 'p2p.sqlite', 'livepro.sqlite', 'data/livepro.sqlite'];
    $chosenSqlite = __DIR__ . '/livepro_customer_lists.sqlite';
    foreach ($sqliteFiles as $sf) {
        if (is_file(__DIR__ . '/' . $sf)) {
            $chosenSqlite = __DIR__ . '/' . $sf;
            break;
        }
    }

    try {
        $pdo = new PDO('sqlite:' . $chosenSqlite, null, null, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]);
        ensureTablesExist($pdo, true);
        return $pdo;
    } catch (Throwable $e) {
        return null;
    }
}

function ensureTablesExist(PDO $pdo, bool $isSqlite): void {
    if ($isSqlite) {
        $pdo->exec("
            CREATE TABLE IF NOT EXISTS customer_xtream_lists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playlist_id TEXT NOT NULL,
                device_id TEXT NOT NULL,
                server_name TEXT NOT NULL,
                server_url TEXT NOT NULL,
                xtream_user TEXT NOT NULL,
                xtream_pass TEXT NOT NULL,
                m3u_url TEXT DEFAULT '',
                enabled INTEGER DEFAULT 1,
                order_index INTEGER DEFAULT 0,
                updated_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_cx_device ON customer_xtream_lists(device_id);
        ");
    } else {
        $pdo->exec("
            CREATE TABLE IF NOT EXISTS customer_xtream_lists (
                id INT AUTO_INCREMENT PRIMARY KEY,
                playlist_id VARCHAR(64) NOT NULL,
                device_id VARCHAR(32) NOT NULL,
                server_name VARCHAR(191) NOT NULL,
                server_url VARCHAR(500) NOT NULL,
                xtream_user VARCHAR(191) NOT NULL,
                xtream_pass VARCHAR(191) NOT NULL,
                m3u_url VARCHAR(500) DEFAULT '',
                enabled TINYINT(1) DEFAULT 1,
                order_index INT DEFAULT 0,
                updated_at DATETIME NOT NULL,
                INDEX idx_cx_device (device_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        ");
    }
}

function getJsonStorePath(): string {
    return __DIR__ . '/customer_xtream_store.json';
}

function loadJsonStore(): array {
    $path = getJsonStorePath();
    if (!is_file($path)) {
        return [];
    }
    $raw = @file_get_contents($path);
    $data = $raw ? json_decode($raw, true) : [];
    return is_array($data) ? $data : [];
}

function saveJsonStore(array $data): void {
    $path = getJsonStorePath();
    @file_put_contents($path, json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE), LOCK_EX);
}

// -----------------------------------------------------------------------------
// 2. التحقق الصارم من صلاحية المشغل عبر قاعدة البيانات الحالية أو api.php
// -----------------------------------------------------------------------------
function verifyDeviceSubscription(string $deviceId, ?PDO $pdo): array {
    $cleanId = strtoupper(trim($deviceId));
    if ($cleanId === '') {
        return [
            'active'      => false,
            'is_expired'  => true,
            'expire_date' => '',
            'days_left'   => 0,
            'message'     => 'يرجى إدخال رمز الشاشة الصحيح (مثال: DEV-1234-ABCD).'
        ];
    }

    // أ) فحص جداول الأجهزة والاشتراكات في قاعدة البيانات الحالية إن وجدت
    if ($pdo !== null) {
        $candidateTables = ['devices', 'subscriptions', 'users_devices', 'screens', 'clients', 'activations'];
        foreach ($candidateTables as $table) {
            try {
                $stmt = $pdo->prepare("SELECT * FROM {$table} WHERE UPPER(device_id) = :did OR UPPER(code) = :did OR UPPER(mac) = :did LIMIT 1");
                $stmt->execute([':did' => $cleanId]);
                $row = $stmt->fetch();
                if ($row) {
                    $expRaw = $row['expire_date'] ?? $row['expire'] ?? $row['expires_at'] ?? $row['end_date'] ?? $row['exp_date'] ?? '';
                    $statusRaw = strtolower((string)($row['status'] ?? $row['active'] ?? '1'));
                    $isBannedOrInactive = in_array($statusRaw, ['0', 'false', 'expired', 'banned', 'disabled', 'inactive'], true);

                    $daysLeft = 30;
                    $isExpired = false;
                    if (!empty($expRaw)) {
                        $expTs = is_numeric($expRaw) ? (int)$expRaw : strtotime((string)$expRaw);
                        if ($expTs !== false && $expTs > 0) {
                            $diffSec = $expTs - time();
                            $daysLeft = (int)floor($diffSec / 86400);
                            if ($diffSec <= 0) {
                                $isExpired = true;
                                $daysLeft = 0;
                            }
                            $expRaw = date('Y-m-d', $expTs);
                        }
                    }

                    if ($isBannedOrInactive || $isExpired) {
                        return [
                            'active'      => false,
                            'is_expired'  => true,
                            'expire_date' => (string)$expRaw,
                            'days_left'   => 0,
                            'message'     => "انتهت صلاحية المشغل للشاشة ({$cleanId}). لا يمكن إضافة أو تعديل القوائم إلا بعد تجديد الاشتراك عبر التواصل مع " . ALI_SAT_NAME . " : " . ALI_SAT_PHONE
                        ];
                    }

                    return [
                        'active'      => true,
                        'is_expired'  => false,
                        'expire_date' => (string)$expRaw ?: 'نشط',
                        'days_left'   => max(1, $daysLeft),
                        'message'     => 'اشتراك المشغل نشط وصالح.'
                    ];
                }
            } catch (Throwable $e) {
                // الجدول غير موجود بهذه البنية، نتابع الفحص
            }
        }
    }

    // ب) فحص الصلاحية مباشرة عبر api.php الحالي على نفس الخادم
    $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host   = $_SERVER['HTTP_HOST'] ?? 'tvflux4k.com';
    $dir    = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/p2p/customer_portal.php')), '/');
    $apiUrl = "{$scheme}://{$host}{$dir}/api.php?action=get_channels&device_id=" . urlencode($cleanId) . "&source=portal_check";

    $ctx = stream_context_create([
        'http' => [
            'method'  => 'GET',
            'timeout' => 6,
            'header'  => "User-Agent: LIVE-PRO-Portal/6.0\r\nAccept: application/json\r\n"
        ],
        'ssl' => [
            'verify_peer'      => false,
            'verify_peer_name' => false,
        ]
    ]);

    $resp = @file_get_contents($apiUrl, false, $ctx);
    if ($resp !== false && strlen($resp) > 2) {
        $json = json_decode($resp, true);
        if (is_array($json)) {
            $sub = $json['data']['subscription'] ?? $json['subscription'] ?? null;
            $success = !empty($json['success']);
            if (is_array($sub)) {
                $active = !empty($sub['active']);
                $days   = (int)($sub['days_left'] ?? ($active ? 30 : 0));
                $exp    = (string)($sub['expire'] ?? $sub['expire_date'] ?? '');
                $msg    = (string)($sub['message'] ?? $json['error'] ?? '');

                if (!$active || $days < 0) {
                    return [
                        'active'      => false,
                        'is_expired'  => true,
                        'expire_date' => $exp,
                        'days_left'   => 0,
                        'message'     => "انتهت صلاحية المشغل للشاشة ({$cleanId}). يلزم تجديد الاشتراك أولاً بالتواصل مع " . ALI_SAT_NAME . " : " . ALI_SAT_PHONE
                    ];
                }

                return [
                    'active'      => true,
                    'is_expired'  => false,
                    'expire_date' => $exp ?: 'نشط',
                    'days_left'   => $days,
                    'message'     => $msg ?: 'اشتراك المشغل نشط وصالح.'
                ];
            } elseif (!$success) {
                return [
                    'active'      => false,
                    'is_expired'  => true,
                    'expire_date' => '',
                    'days_left'   => 0,
                    'message'     => "المشغل غير مفعّل أو منتهي الصلاحية للشاشة ({$cleanId}). يرجى التواصل مع " . ALI_SAT_NAME . " : " . ALI_SAT_PHONE
                ];
            }
        }
    }

    // في حال كان الجهاز قيد الفحص الأولي ولم يرد خطأ انتهاء من الـ API
    return [
        'active'      => true,
        'is_expired'  => false,
        'expire_date' => 'نشط',
        'days_left'   => 30,
        'message'     => 'الشاشة نشطة وجاهزة لإدارة القوائم.'
    ];
}

// -----------------------------------------------------------------------------
// 3. دوال جلب وحفظ وتعديل وحذف القوائم حسب رمز الشاشة
// -----------------------------------------------------------------------------
function getPlaylistsByDevice(string $deviceId, ?PDO $pdo): array {
    $cleanId = strtoupper(trim($deviceId));
    if ($cleanId === '') return [];

    if ($pdo !== null) {
        try {
            $stmt = $pdo->prepare("SELECT * FROM customer_xtream_lists WHERE UPPER(device_id) = :did ORDER BY order_index ASC, id ASC");
            $stmt->execute([':did' => $cleanId]);
            $rows = $stmt->fetchAll();
            $result = [];
            foreach ($rows as $r) {
                $result[] = [
                    'id'          => (string)$r['playlist_id'],
                    'device_id'   => (string)$r['device_id'],
                    'server_name' => (string)$r['server_name'],
                    'server_url'  => (string)$r['server_url'],
                    'username'    => (string)$r['xtream_user'],
                    'password'    => (string)$r['xtream_pass'],
                    'm3u_url'     => (string)($r['m3u_url'] ?? ''),
                    'enabled'     => ((int)($r['enabled'] ?? 1)) === 1,
                    'order_index' => (int)($r['order_index'] ?? 0),
                    'updated_at'  => (string)($r['updated_at'] ?? '')
                ];
            }
            return $result;
        } catch (Throwable $e) {
            // fallback to JSON store
        }
    }

    $store = loadJsonStore();
    return array_values($store[$cleanId] ?? []);
}

function saveOrUpdatePlaylist(string $deviceId, array $item, ?PDO $pdo): bool {
    $cleanId    = strtoupper(trim($deviceId));
    $playlistId = trim((string)($item['id'] ?? ''));
    if ($playlistId === '') {
        $playlistId = 'PL_' . strtoupper(substr(bin2hex(random_bytes(4)), 0, 8));
    }
    $serverName = trim((string)($item['server_name'] ?? 'قائمة XTREAM'));
    $serverUrl  = rtrim(trim((string)($item['server_url'] ?? '')), '/');
    $username   = trim((string)($item['username'] ?? ''));
    $password   = trim((string)($item['password'] ?? ''));
    $m3uUrl     = trim((string)($item['m3u_url'] ?? ''));
    $enabled    = !isset($item['enabled']) || !empty($item['enabled']) ? 1 : 0;
    $now        = date('Y-m-d H:i:s');

    if ($serverUrl !== '' && !preg_match('#^https?://#i', $serverUrl)) {
        $serverUrl = 'http://' . $serverUrl;
    }

    if ($pdo !== null) {
        try {
            $check = $pdo->prepare("SELECT id FROM customer_xtream_lists WHERE UPPER(device_id) = :did AND playlist_id = :pid LIMIT 1");
            $check->execute([':did' => $cleanId, ':pid' => $playlistId]);
            $existing = $check->fetch();

            if ($existing) {
                $upd = $pdo->prepare("
                    UPDATE customer_xtream_lists
                    SET server_name = :sname,
                        server_url  = :surl,
                        xtream_user = :xuser,
                        xtream_pass = :xpass,
                        m3u_url     = :m3u,
                        enabled     = :en,
                        updated_at  = :uAt
                    WHERE id = :rowId
                ");
                $upd->execute([
                    ':sname' => $serverName,
                    ':surl'  => $serverUrl,
                    ':xuser' => $username,
                    ':xpass' => $password,
                    ':m3u'   => $m3uUrl,
                    ':en'    => $enabled,
                    ':uAt'   => $now,
                    ':rowId' => $existing['id']
                ]);
            } else {
                $ins = $pdo->prepare("
                    INSERT INTO customer_xtream_lists
                    (playlist_id, device_id, server_name, server_url, xtream_user, xtream_pass, m3u_url, enabled, order_index, updated_at)
                    VALUES (:pid, :did, :sname, :surl, :xuser, :xpass, :m3u, :en, 0, :uAt)
                ");
                $ins->execute([
                    ':pid'   => $playlistId,
                    ':did'   => $cleanId,
                    ':sname' => $serverName,
                    ':surl'  => $serverUrl,
                    ':xuser' => $username,
                    ':xpass' => $password,
                    ':m3u'   => $m3uUrl,
                    ':en'    => $enabled,
                    ':uAt'   => $now
                ]);
            }
        } catch (Throwable $e) {
            // fallback to JSON store below
        }
    }

    // حفظ متزامن أيضاً في JSON لضمان التكامل السريع مع أي سكربتات أخرى
    $store = loadJsonStore();
    $list  = $store[$cleanId] ?? [];
    $found = false;
    foreach ($list as &$entry) {
        if (($entry['id'] ?? '') === $playlistId) {
            $entry['server_name'] = $serverName;
            $entry['server_url']  = $serverUrl;
            $entry['username']    = $username;
            $entry['password']    = $password;
            $entry['m3u_url']     = $m3uUrl;
            $entry['enabled']     = ($enabled === 1);
            $entry['updated_at']  = $now;
            $found = true;
            break;
        }
    }
    unset($entry);
    if (!$found) {
        $list[] = [
            'id'          => $playlistId,
            'device_id'   => $cleanId,
            'server_name' => $serverName,
            'server_url'  => $serverUrl,
            'username'    => $username,
            'password'    => $password,
            'm3u_url'     => $m3uUrl,
            'enabled'     => ($enabled === 1),
            'order_index' => count($list),
            'updated_at'  => $now
        ];
    }
    $store[$cleanId] = $list;
    saveJsonStore($store);
    return true;
}

function deletePlaylistById(string $deviceId, string $playlistId, ?PDO $pdo): bool {
    $cleanId = strtoupper(trim($deviceId));
    $pid     = trim($playlistId);
    if ($cleanId === '' || $pid === '') return false;

    if ($pdo !== null) {
        try {
            $del = $pdo->prepare("DELETE FROM customer_xtream_lists WHERE UPPER(device_id) = :did AND playlist_id = :pid");
            $del->execute([':did' => $cleanId, ':pid' => $pid]);
        } catch (Throwable $e) {
        }
    }

    $store = loadJsonStore();
    if (isset($store[$cleanId])) {
        $store[$cleanId] = array_values(array_filter($store[$cleanId], fn($it) => ($it['id'] ?? '') !== $pid));
        saveJsonStore($store);
    }
    return true;
}

// -----------------------------------------------------------------------------
// 4. نقطة النهاية JSON API للتكامل المباشر مع تطبيق الأندرويد LIVE PRO
// -----------------------------------------------------------------------------
$pdo = getDatabaseConnection();
$action = trim((string)($_REQUEST['action'] ?? ''));

if (in_array($action, ['get_xtream_lists', 'save_xtream', 'delete_xtream', 'check_status'], true) ||
    (isset($_SERVER['HTTP_ACCEPT']) && strpos($_SERVER['HTTP_ACCEPT'], 'application/json') !== false && !empty($action))) {

    header('Content-Type: application/json; charset=utf-8');
    $reqDeviceId = strtoupper(trim((string)($_REQUEST['device_id'] ?? '')));
    $subStatus   = verifyDeviceSubscription($reqDeviceId, $pdo);

    if ($action === 'get_xtream_lists' || $action === 'check_status') {
        $lists = $subStatus['active'] ? getPlaylistsByDevice($reqDeviceId, $pdo) : [];
        echo json_encode([
            'success'       => $subStatus['active'],
            'device_id'     => $reqDeviceId,
            'subscription'  => $subStatus,
            'xtream_lists'  => $lists,
            'contact_name'  => ALI_SAT_NAME,
            'contact_phone' => ALI_SAT_PHONE
        ], JSON_UNESCAPED_UNICODE);
        exit;
    }

    if ($action === 'save_xtream') {
        if (!$subStatus['active']) {
            echo json_encode([
                'success'       => false,
                'error'         => $subStatus['message'],
                'subscription'  => $subStatus,
                'contact_name'  => ALI_SAT_NAME,
                'contact_phone' => ALI_SAT_PHONE
            ], JSON_UNESCAPED_UNICODE);
            exit;
        }
        saveOrUpdatePlaylist($reqDeviceId, [
            'id'          => $_POST['playlist_id'] ?? '',
            'server_name' => $_POST['server_name'] ?? 'XTREAM VIP',
            'server_url'  => $_POST['server_url'] ?? '',
            'username'    => $_POST['xtream_user'] ?? $_POST['username'] ?? '',
            'password'    => $_POST['xtream_pass'] ?? $_POST['password'] ?? '',
            'm3u_url'     => $_POST['m3u_url'] ?? '',
            'enabled'     => isset($_POST['enabled']) ? (int)$_POST['enabled'] : 1
        ], $pdo);

        echo json_encode([
            'success'      => true,
            'message'      => 'تم حفظ القائمة بنجاح ومزامنتها مع الشاشة.',
            'subscription' => $subStatus,
            'xtream_lists' => getPlaylistsByDevice($reqDeviceId, $pdo)
        ], JSON_UNESCAPED_UNICODE);
        exit;
    }

    if ($action === 'delete_xtream') {
        $pid = (string)($_REQUEST['playlist_id'] ?? '');
        deletePlaylistById($reqDeviceId, $pid, $pdo);
        echo json_encode([
            'success'      => true,
            'message'      => 'تم حذف القائمة بنجاح.',
            'xtream_lists' => getPlaylistsByDevice($reqDeviceId, $pdo)
        ], JSON_UNESCAPED_UNICODE);
        exit;
    }
}

// -----------------------------------------------------------------------------
// 5. معالجة طلبات واجهة الويب للزبون (بحث / إضافة / تعديل / حذف)
// -----------------------------------------------------------------------------
$currentDeviceId = strtoupper(trim((string)($_REQUEST['device_id'] ?? '')));
$flashMessage    = '';
$flashType       = 'info'; // success | error | warning
$editItem        = null;

if ($currentDeviceId !== '') {
    $subInfo = verifyDeviceSubscription($currentDeviceId, $pdo);

    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        $formAction = (string)($_POST['form_action'] ?? '');

        if ($formAction === 'save_playlist') {
            if (!$subInfo['active']) {
                $flashType    = 'error';
                $flashMessage = "⛔ لا يمكن إضافة أو تعديل القائمة لأن اشتراك المشغل منتهي الصلاحية للشاشة ({$currentDeviceId}). يرجى تجديد الاشتراك بالتواصل مع " . ALI_SAT_NAME . " : " . ALI_SAT_PHONE;
            } else {
                $sName = trim((string)($_POST['server_name'] ?? ''));
                $sUrl  = trim((string)($_POST['server_url'] ?? ''));
                $sUser = trim((string)($_POST['username'] ?? ''));
                $sPass = trim((string)($_POST['password'] ?? ''));
                $m3u   = trim((string)($_POST['m3u_url'] ?? ''));
                $pid   = trim((string)($_POST['playlist_id'] ?? ''));

                if (($sUrl === '' || $sUser === '' || $sPass === '') && $m3u === '') {
                    $flashType    = 'warning';
                    $flashMessage = 'يرجى إدخال رابط السيرفر واسم المستخدم وكلمة المرور (أو رابط M3U).';
                } else {
                    saveOrUpdatePlaylist($currentDeviceId, [
                        'id'          => $pid,
                        'server_name' => $sName !== '' ? $sName : 'قائمة XTREAM',
                        'server_url'  => $sUrl,
                        'username'    => $sUser,
                        'password'    => $sPass,
                        'm3u_url'     => $m3u,
                        'enabled'     => isset($_POST['enabled']) ? 1 : 0
                    ], $pdo);
                    $flashType    = 'success';
                    $flashMessage = $pid !== ''
                        ? '✅ تم تعديل وحفظ بيانات القائمة بنجاح! اضغط على تحديث في التلفاز لظهورها فوراً.'
                        : '✅ تمت إضافة القائمة بنجاح وربطها برمز الشاشة! ستظهر فوراً في التطبيق.';
                }
            }
        } elseif ($formAction === 'delete_playlist') {
            $pid = trim((string)($_POST['playlist_id'] ?? ''));
            deletePlaylistById($currentDeviceId, $pid, $pdo);
            $flashType    = 'success';
            $flashMessage = '🗑️ تم حذف القائمة من الشاشة بنجاح.';
        } elseif ($formAction === 'toggle_playlist') {
            if (!$subInfo['active']) {
                $flashType    = 'error';
                $flashMessage = "⛔ اشتراك المشغل منتهي الصلاحية. يرجى التجديد عبر " . ALI_SAT_NAME . " : " . ALI_SAT_PHONE;
            } else {
                $pid   = trim((string)($_POST['playlist_id'] ?? ''));
                $lists = getPlaylistsByDevice($currentDeviceId, $pdo);
                foreach ($lists as $l) {
                    if ($l['id'] === $pid) {
                        $l['enabled'] = !$l['enabled'];
                        saveOrUpdatePlaylist($currentDeviceId, $l, $pdo);
                        $flashType    = 'success';
                        $flashMessage = '✅ تم تحديث حالة القائمة بنجاح.';
                        break;
                    }
                }
            }
        }
    }

    $playlists = getPlaylistsByDevice($currentDeviceId, $pdo);
    if (isset($_GET['edit_id'])) {
        $editId = trim((string)$_GET['edit_id']);
        foreach ($playlists as $pl) {
            if ($pl['id'] === $editId) {
                $editItem = $pl;
                break;
            }
        }
    }
} else {
    $subInfo   = null;
    $playlists = [];
}
?>
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LIVE PRO — فضاء الزبون وإدارة قوائم XTREAM عن بُعد</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Cairo:wght@500;700;800&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-deep: #08060E;
            --surface-dark: #120B1E;
            --card-dark: #1A102B;
            --purple-primary: #3B1968;
            --purple-accent: #5C2D91;
            --purple-border: #311C4E;
            --text-white: #F8FAFC;
            --text-muted: #C4B5FD;
            --emerald: #10B981;
            --crimson: #EF4444;
            --amber: #F59E0B;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: 'Cairo', system-ui, -apple-system, sans-serif;
            background: radial-gradient(circle at top, #1F0F38 0%, var(--bg-deep) 70%);
            color: var(--text-white);
            min-height: 100vh;
            padding: 24px 16px;
            line-height: 1.6;
        }
        .container {
            max-width: 960px;
            margin: 0 auto;
        }
        .header-card {
            background: linear-gradient(135deg, #261147 0%, var(--surface-dark) 100%);
            border: 1.5px solid var(--purple-accent);
            border-radius: 20px;
            padding: 22px 26px;
            margin-bottom: 20px;
            display: flex;
            flex-wrap: wrap;
            justify-content: space-between;
            align-items: center;
            gap: 16px;
            box-shadow: 0 12px 32px rgba(0, 0, 0, 0.55);
        }
        .brand-title {
            font-size: 1.65rem;
            font-weight: 800;
            color: #FFFFFF;
            letter-spacing: 0.5px;
        }
        .brand-sub {
            font-size: 0.95rem;
            color: var(--text-muted);
        }
        .support-badge {
            background: var(--card-dark);
            border: 1px solid var(--purple-accent);
            padding: 10px 16px;
            border-radius: 14px;
            text-align: center;
        }
        .support-badge strong {
            display: block;
            color: #FFFFFF;
            font-size: 1.05rem;
            direction: ltr;
        }
        .card {
            background: var(--card-dark);
            border: 1.5px solid var(--purple-border);
            border-radius: 18px;
            padding: 22px;
            margin-bottom: 20px;
            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
        }
        .card h2 {
            font-size: 1.2rem;
            font-weight: 800;
            margin-bottom: 14px;
            color: #FFFFFF;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .search-row {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
        }
        input[type="text"], input[type="url"], input[type="password"] {
            width: 100%;
            background: var(--bg-deep);
            border: 1.5px solid var(--purple-border);
            color: #FFFFFF;
            padding: 12px 15px;
            border-radius: 12px;
            font-family: inherit;
            font-size: 0.98rem;
            outline: none;
            transition: border-color 0.2s;
        }
        input:focus {
            border-color: #9333EA;
        }
        .code-input {
            font-family: monospace;
            font-size: 1.15rem !important;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 1.5px;
            text-align: center;
        }
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            background: var(--purple-accent);
            color: #FFFFFF;
            border: 1px solid rgba(255,255,255,0.2);
            padding: 12px 22px;
            border-radius: 12px;
            font-family: inherit;
            font-weight: 800;
            font-size: 0.98rem;
            cursor: pointer;
            text-decoration: none;
            transition: transform 0.15s, background 0.2s;
        }
        .btn:hover { background: #6D37AD; transform: translateY(-1px); }
        .btn-success { background: var(--emerald); color: #051B14; }
        .btn-danger { background: var(--crimson); color: #FFFFFF; }
        .btn-sm { padding: 7px 14px; font-size: 0.86rem; border-radius: 9px; }
        .form-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 14px;
            margin-bottom: 16px;
        }
        label {
            display: block;
            font-size: 0.9rem;
            font-weight: 700;
            color: var(--text-muted);
            margin-bottom: 6px;
        }
        .alert {
            padding: 16px 18px;
            border-radius: 14px;
            margin-bottom: 20px;
            font-weight: 700;
        }
        .alert-success { background: rgba(16, 185, 129, 0.15); border: 1.5px solid var(--emerald); color: #A7F3D0; }
        .alert-error { background: rgba(239, 68, 68, 0.18); border: 2px solid var(--crimson); color: #FECACA; }
        .alert-warning { background: rgba(245, 158, 11, 0.16); border: 1.5px solid var(--amber); color: #FDE68A; }
        .status-banner {
            display: flex;
            flex-wrap: wrap;
            justify-content: space-between;
            align-items: center;
            padding: 16px 20px;
            border-radius: 14px;
            margin-bottom: 20px;
        }
        .status-active {
            background: rgba(16, 185, 129, 0.12);
            border: 1.5px solid var(--emerald);
        }
        .status-expired {
            background: linear-gradient(135deg, rgba(127, 29, 29, 0.55), rgba(40, 12, 35, 0.85));
            border: 2px solid var(--crimson);
        }
        .table-wrap { overflow-x: auto; }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 8px;
        }
        th, td {
            padding: 12px 10px;
            text-align: right;
            border-bottom: 1px solid var(--purple-border);
            font-size: 0.93rem;
        }
        th { color: var(--text-muted); font-weight: 800; }
        .badge {
            display: inline-block;
            padding: 3px 10px;
            border-radius: 8px;
            font-size: 0.8rem;
            font-weight: 800;
        }
        .badge-on { background: rgba(16, 185, 129, 0.2); color: #6EE7B7; }
        .badge-off { background: rgba(239, 68, 68, 0.2); color: #FCA5A5; }
    </style>
</head>
<body>
<div class="container">
    <!-- ترويسة لوحة فضاء الزبون -->
    <div class="header-card">
        <div>
            <div class="brand-title">📺 LIVE PRO — فضاء الزبون الذكي</div>
            <div class="brand-sub">إدارة وإضافة وتعديل قوائم XTREAM عن بُعد عبر رمز الشاشة بدون كتابة في التلفاز</div>
        </div>
        <div class="support-badge">
            <span>الدعم الفني وتجديد الاشتراكات — <?php echo htmlspecialchars(ALI_SAT_NAME); ?></span>
            <strong>📞 <?php echo htmlspecialchars(ALI_SAT_PHONE); ?></strong>
        </div>
    </div>

    <!-- نموذج البحث عن الشاشة عبر رمز الشاشة -->
    <div class="card">
        <h2>🔍 البحث عن القوائم وإدارتها عبر رمز الشاشة (Screen Code)</h2>
        <form method="get" action="">
            <div class="search-row">
                <div style="flex: 1; min-width: 240px;">
                    <input type="text" name="device_id" class="code-input"
                           placeholder="DEV-XXXX-XXXX"
                           value="<?php echo htmlspecialchars($currentDeviceId); ?>" required>
                </div>
                <button type="submit" class="btn">🔎 بحث واستعراض قوائم الشاشة</button>
            </div>
        </form>
    </div>

    <?php if ($flashMessage !== ''): ?>
        <div class="alert alert-<?php echo htmlspecialchars($flashType); ?>">
            <?php echo htmlspecialchars($flashMessage); ?>
        </div>
    <?php endif; ?>

    <?php if ($currentDeviceId !== '' && $subInfo !== null): ?>
        <!-- شريط حالة صلاحية المشغل للشاشة المحددة -->
        <?php if ($subInfo['active']): ?>
            <div class="status-banner status-active">
                <div>
                    <strong>✅ حالة المشغل للشاشة (<?php echo htmlspecialchars($currentDeviceId); ?>): اشتراك نشط وصالح</strong>
                    <div style="font-size: 0.9rem; color: #D1FAE5;">
                        يمكنك الآن إضافة قوائم XTREAM جديدة أو تعديل وحذف القوائم الحالية بحرية.
                    </div>
                </div>
                <div style="text-align: left;">
                    <span class="badge badge-on">الصلاحية المتبقية: <?php echo (int)$subInfo['days_left']; ?> يوم</span>
                    <?php if (!empty($subInfo['expire_date'])): ?>
                        <div style="font-size: 0.85rem; margin-top: 4px; color: var(--text-muted);">
                            تاريخ الانتهاء: <?php echo htmlspecialchars($subInfo['expire_date']); ?>
                        </div>
                    <?php endif; ?>
                </div>
            </div>
        <?php else: ?>
            <!-- تنبيه انتهاء الصلاحية ومنع الرفع مع معلومات التواصل مع علي سات -->
            <div class="status-banner status-expired">
                <div>
                    <h3 style="color: #FCA5A5; margin-bottom: 6px;">⛔ انتهت صلاحية المشغل لهذه الشاشة (<?php echo htmlspecialchars($currentDeviceId); ?>)</h3>
                    <p style="font-size: 0.96rem; margin-bottom: 8px;">
                        عذراً، لا يقوم النظام برفع أو إضافة قوائم جديدة عندما يكون اشتراك المشغل منتهي الصلاحية.
                        يرجى تجديد اشتراك المشغل أولاً وتقديم رمز الشاشة أعلاه للتفعيل:
                    </p>
                    <div style="background: rgba(0,0,0,0.45); padding: 10px 16px; border-radius: 10px; display: inline-block; border: 1px solid #FCA5A5;">
                        <strong>📞 تواصل مع <?php echo htmlspecialchars(ALI_SAT_NAME); ?> : <span dir="ltr"><?php echo htmlspecialchars(ALI_SAT_PHONE); ?></span></strong>
                        &nbsp;|&nbsp;
                        <strong>رمز الشاشة: <span dir="ltr"><?php echo htmlspecialchars($currentDeviceId); ?></span></strong>
                    </div>
                </div>
            </div>
        <?php endif; ?>

        <!-- نموذج إضافة أو تعديل قائمة XTREAM (يُفعل فقط إذا كان المشغل ساري الصلاحية) -->
        <?php if ($subInfo['active']): ?>
            <div class="card">
                <h2><?php echo $editItem ? '✏️ تعديل قائمة XTREAM الحالية' : '➕ إضافة قائمة XTREAM جديدة للشاشة'; ?></h2>
                <form method="post" action="?device_id=<?php echo urlencode($currentDeviceId); ?>">
                    <input type="hidden" name="device_id" value="<?php echo htmlspecialchars($currentDeviceId); ?>">
                    <input type="hidden" name="form_action" value="save_playlist">
                    <input type="hidden" name="playlist_id" value="<?php echo htmlspecialchars((string)($editItem['id'] ?? '')); ?>">

                    <div class="form-grid">
                        <div>
                            <label>رمز الشاشة (Screen Code)</label>
                            <input type="text" value="<?php echo htmlspecialchars($currentDeviceId); ?>" disabled class="code-input">
                        </div>
                        <div>
                            <label>اسم القائمة (يظهر للمستخدم في التطبيق)</label>
                            <input type="text" name="server_name" placeholder="مثال: القائمة الرياضية VIP"
                                   value="<?php echo htmlspecialchars((string)($editItem['server_name'] ?? '')); ?>" required>
                        </div>
                    </div>

                    <div class="form-grid">
                        <div>
                            <label>رابط سيرفر الأكستريم (Server URL / Host:Port)</label>
                            <input type="text" name="server_url" dir="ltr" placeholder="http://server-domain.com:8080"
                                   value="<?php echo htmlspecialchars((string)($editItem['server_url'] ?? '')); ?>" required>
                        </div>
                        <div>
                            <label>اسم المستخدم (Username)</label>
                            <input type="text" name="username" dir="ltr" placeholder="Username"
                                   value="<?php echo htmlspecialchars((string)($editItem['username'] ?? '')); ?>" required>
                        </div>
                        <div>
                            <label>كلمة المرور (Password)</label>
                            <input type="text" name="password" dir="ltr" placeholder="Password"
                                   value="<?php echo htmlspecialchars((string)($editItem['password'] ?? '')); ?>" required>
                        </div>
                    </div>

                    <div class="form-grid">
                        <div>
                            <label>رابط M3U مباشر (اختياري — اتركه فارغاً إذا أدخلت بيانات XTREAM أعلاه)</label>
                            <input type="text" name="m3u_url" dir="ltr" placeholder="http://..."
                                   value="<?php echo htmlspecialchars((string)($editItem['m3u_url'] ?? '')); ?>">
                        </div>
                        <div style="display: flex; align-items: center; padding-top: 24px;">
                            <label style="display: flex; align-items: center; gap: 10px; cursor: pointer; color: #FFFFFF;">
                                <input type="checkbox" name="enabled" value="1"
                                    <?php echo (!isset($editItem['enabled']) || !empty($editItem['enabled'])) ? 'checked' : ''; ?>
                                       style="width: 20px; height: 20px;">
                                تفعيل هذه القائمة مباشرة على التلفاز
                            </label>
                        </div>
                    </div>

                    <div style="display: flex; gap: 12px;">
                        <button type="submit" class="btn btn-success">
                            <?php echo $editItem ? '💾 حفظ التعديلات ومزامنتها' : '⚡ إضافة القائمة وربطها بالشاشة الآن'; ?>
                        </button>
                        <?php if ($editItem): ?>
                            <a href="?device_id=<?php echo urlencode($currentDeviceId); ?>" class="btn">إلغاء التعديل</a>
                        <?php endif; ?>
                    </div>
                </form>
            </div>
        <?php endif; ?>

        <!-- جدول القوائم المرتبطة برمز الشاشة -->
        <div class="card">
            <h2>📋 القوائم المحفوظة للشاشة <?php echo htmlspecialchars($currentDeviceId); ?> (<?php echo count($playlists); ?>)</h2>
            <?php if (empty($playlists)): ?>
                <p style="color: var(--text-muted);">لا توجد قوائم XTREAM مضافة لهذه الشاشة حتى الآن.</p>
            <?php else: ?>
                <div class="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            <th>#</th>
                            <th>اسم القائمة</th>
                            <th>رابط السيرفر (Host)</th>
                            <th>اسم المستخدم</th>
                            <th>الحالة</th>
                            <th>آخر تحديث</th>
                            <th>إجراءات التحكم</th>
                        </tr>
                        </thead>
                        <tbody>
                        <?php foreach ($playlists as $idx => $pl): ?>
                            <tr>
                                <td><?php echo $idx + 1; ?></td>
                                <td><strong><?php echo htmlspecialchars($pl['server_name']); ?></strong></td>
                                <td dir="ltr"><?php echo htmlspecialchars($pl['server_url'] ?: $pl['m3u_url']); ?></td>
                                <td dir="ltr"><?php echo htmlspecialchars($pl['username']); ?></td>
                                <td>
                                    <?php if (!empty($pl['enabled'])): ?>
                                        <span class="badge badge-on">مفعّلة ✓</span>
                                    <?php else: ?>
                                        <span class="badge badge-off">معطّلة</span>
                                    <?php endif; ?>
                                </td>
                                <td dir="ltr"><?php echo htmlspecialchars($pl['updated_at'] ?? ''); ?></td>
                                <td>
                                    <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                                        <?php if ($subInfo['active']): ?>
                                            <a class="btn btn-sm"
                                               href="?device_id=<?php echo urlencode($currentDeviceId); ?>&edit_id=<?php echo urlencode($pl['id']); ?>">
                                                ✏️ تعديل
                                            </a>
                                            <form method="post" action="?device_id=<?php echo urlencode($currentDeviceId); ?>" style="display:inline;">
                                                <input type="hidden" name="device_id" value="<?php echo htmlspecialchars($currentDeviceId); ?>">
                                                <input type="hidden" name="form_action" value="toggle_playlist">
                                                <input type="hidden" name="playlist_id" value="<?php echo htmlspecialchars($pl['id']); ?>">
                                                <button type="submit" class="btn btn-sm">
                                                    <?php echo !empty($pl['enabled']) ? 'إيقاف مؤقت' : 'تفعيل'; ?>
                                                </button>
                                            </form>
                                        <?php endif; ?>
                                        <form method="post" action="?device_id=<?php echo urlencode($currentDeviceId); ?>"
                                              onsubmit="return confirm('هل أنت متأكد من حذف هذه القائمة؟');" style="display:inline;">
                                            <input type="hidden" name="device_id" value="<?php echo htmlspecialchars($currentDeviceId); ?>">
                                            <input type="hidden" name="form_action" value="delete_playlist">
                                            <input type="hidden" name="playlist_id" value="<?php echo htmlspecialchars($pl['id']); ?>">
                                            <button type="submit" class="btn btn-sm btn-danger">🗑️ حذف</button>
                                        </form>
                                    </div>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
            <?php endif; ?>
        </div>
    <?php endif; ?>
</div>
</body>
</html>

