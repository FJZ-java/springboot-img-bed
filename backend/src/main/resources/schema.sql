CREATE TABLE IF NOT EXISTS user (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT    NOT NULL UNIQUE,
    password    TEXT    NOT NULL,
    nickname    TEXT,
    status      INTEGER NOT NULL DEFAULT 1,
    create_time TEXT    NOT NULL
);

-- 账号状态：1 正常 / 0 封禁，封禁后无法登录

CREATE TABLE IF NOT EXISTS upload_record (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL,
    file_name    TEXT    NOT NULL,
    origin_name  TEXT    NOT NULL,
    github_path  TEXT    NOT NULL,
    sha          TEXT,
    cdn_url      TEXT    NOT NULL,
    size         INTEGER NOT NULL DEFAULT 0,
    content_type TEXT,
    create_time  TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_record_user ON upload_record (user_id, create_time);

-- 分享链接：把某条上传记录生成一个可公开访问的短链
CREATE TABLE IF NOT EXISTS share_link (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    code        TEXT    NOT NULL UNIQUE,
    record_id   INTEGER NOT NULL,
    user_id     INTEGER NOT NULL,
    status      INTEGER NOT NULL DEFAULT 1,
    view_count  INTEGER NOT NULL DEFAULT 0,
    create_time TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_share_record ON share_link (record_id);
CREATE INDEX IF NOT EXISTS idx_share_user ON share_link (user_id, create_time);

-- 轮播广告：管理员配置的轮播图（图片地址 / 标题 / 跳转链接 / 排序 / 启停）
CREATE TABLE IF NOT EXISTS carousel_slide (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    image_url   TEXT    NOT NULL,
    title       TEXT,
    link_url    TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    enabled     INTEGER NOT NULL DEFAULT 1,
    create_time TEXT    NOT NULL
);

-- 多仓库存储配置：上传时按权重随机挑一个启用中的仓库
CREATE TABLE IF NOT EXISTS storage_repo (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    owner          TEXT    NOT NULL,
    repo           TEXT    NOT NULL,
    branch         TEXT,
    dir_prefix     TEXT    NOT NULL DEFAULT 'images',
    token          TEXT,
    enabled        INTEGER NOT NULL DEFAULT 1,
    weight         INTEGER NOT NULL DEFAULT 1,
    file_count     INTEGER NOT NULL DEFAULT 0,
    total_size     INTEGER NOT NULL DEFAULT 0,
    disk_size      INTEGER NOT NULL DEFAULT 0,
    remark         TEXT,
    last_sync_time TEXT,
    create_time    TEXT    NOT NULL
);
