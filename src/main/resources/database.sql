create table users
(
    id                uuid primary key default gen_random_uuid() not null,
    email             varchar(255)                               not null
        constraint email
            unique,
    username          varchar(255)                               not null,
    password_hash     varchar(255),
    activated         boolean          default false             not null,
    seen_tour_version int                                        not null default 0,
    created_at        timestamp        default CURRENT_TIMESTAMP,
    updated_at        timestamp        default CURRENT_TIMESTAMP
);

CREATE TABLE categories
(
    id         uuid primary key default gen_random_uuid() not null,
    code       VARCHAR(32)                                NOT NULL, -- 程序用的稳定标识，如 'vegetable'
    name       VARCHAR(64)                                NOT NULL, -- 显示名，如 '蔬菜'
    created_at timestamp        DEFAULT current_timestamp,
    UNIQUE (code)
);

CREATE TABLE tags
(
    id         uuid primary key default gen_random_uuid() not null,
    code       VARCHAR(32)                                NOT NULL, -- 程序用稳定标识，如 'sichuan'
    name       VARCHAR(64)                                NOT NULL, -- 显示名，如 '川菜'
    created_at timestamp        DEFAULT current_timestamp,
        UNIQUE (code)
);

create table ingredients
(
    id              uuid primary key      default gen_random_uuid() not null,
    canonical_name  varchar(256) not null,
    normalized_name varchar(256) not null,
    category_id     uuid REFERENCES categories (id),
    reference_price float8       null     default 0,
    last_purchase   timestamp    null,
    created_at      timestamp    not null default current_timestamp,
    UNIQUE (normalized_name)
);

CREATE TABLE ingredient_aliases
(
    id               uuid primary key default gen_random_uuid() not null,
    ingredient_id    uuid                                       NOT NULL REFERENCES ingredients (id) on delete cascade,
    normalized_alias VARCHAR(256)                               NOT NULL,
    language         VARCHAR(8), -- zh / en
    UNIQUE (normalized_alias)
);

CREATE TABLE recipes
(
    id                uuid primary key      default gen_random_uuid() not null, -- 应用层 v7 生成，不写 DEFAULT
    user_id           UUID         NOT NULL REFERENCES users (id),              -- 上传者
    title             VARCHAR(512) not null,                                    -- 菜名
    description       TEXT,                                                     -- 简介/备注
    prep_time_minutes INT,
    cook_time_minutes INT,
    status            VARCHAR(16)  NOT NULL DEFAULT 'pending',
    is_public         BOOLEAN               DEFAULT false,
    created_at        timestamp             DEFAULT current_timestamp,
    updated_at        timestamp             DEFAULT current_timestamp,
    CONSTRAINT chk_status
        CHECK (status IN ('pending', 'done'))
);

CREATE TABLE recipe_steps
(
    id          uuid primary key default gen_random_uuid() not null,
    recipe_id   UUID                                       NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    step_order  INT                                        NOT NULL,
    instruction TEXT,
    is_optional BOOLEAN          DEFAULT false,
    created_at  timestamp        DEFAULT current_timestamp
);

CREATE TABLE step_ingredients
(
    id            uuid primary key default gen_random_uuid() not null,
    step_id       uuid                                       NOT NULL REFERENCES recipe_steps (id) ON DELETE CASCADE,
    ingredient_id UUID                                       not null REFERENCES ingredients (id),
    amount        float8,
    amount_text   VARCHAR(64),
    unit          VARCHAR(16),
    is_optional   BOOLEAN          DEFAULT false,
    prep_note     text
);

CREATE TABLE recipe_ingredients
(
    id            uuid primary key default gen_random_uuid() not null,
    recipe_id     uuid                                       NOT NULL REFERENCES recipes (id),
    ingredient_id uuid                                       not null REFERENCES ingredients (id),
    is_optional   BOOLEAN          DEFAULT false
);

CREATE TABLE recipe_raw_images
(
    id          uuid primary key default gen_random_uuid() not null,
    recipe_id   UUID                                       NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    storage_key VARCHAR(1024)                              NOT NULL,
    created_at  timestamp        DEFAULT current_timestamp
);

CREATE TABLE recipe_tags
(
    recipe_id  UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    tag_id     uuid NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    source     VARCHAR(16) DEFAULT 'ai', -- ai / user / manual
    created_at timestamp   DEFAULT current_timestamp,
    PRIMARY KEY (recipe_id, tag_id)      -- 复合主键，天然防重复
);

CREATE TABLE favorites
(
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipe_id  UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    created_at timestamp DEFAULT current_timestamp,
    PRIMARY KEY (user_id, recipe_id) -- 同一用户不能重复收藏
);

CREATE INDEX idx_favorites_user_time ON favorites (user_id, created_at DESC);

CREATE TABLE recipe_images
(
    id            uuid primary key default gen_random_uuid() not null,
    recipe_id     UUID         NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    source        VARCHAR(16)  NOT NULL,        -- 'ai' / 'user'：这张图哪来的
    storage_key   VARCHAR(512),                 -- 成功后的对象存储路径
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending',  -- AI图走 pending→done；用户图直接 done
    is_primary    BOOLEAN      DEFAULT false,   -- 是否当前封面
    display_order INT          DEFAULT 0,       -- 多图展示排序

    -- 仅 AI 图用的字段（source='user' 时为空）
    prompt        TEXT,
    model         VARCHAR(128),
    error_message TEXT,

    -- 仅用户图用的字段（source='ai' 时为空）
    uploaded_by   UUID         REFERENCES users (id),  -- 谁上传的

    created_at    timestamp    DEFAULT current_timestamp,
    CONSTRAINT chk_img_source CHECK (source IN ('ai', 'user')),
    CONSTRAINT chk_img_status CHECK (status IN ('pending', 'processing', 'done', 'failed'))
);

CREATE INDEX idx_recipe_images_recipe ON recipe_images (recipe_id);

-- 全菜谱最多一张封面，不分来源——这正是合表的好处
CREATE UNIQUE INDEX uk_recipe_image_primary
    ON recipe_images (recipe_id)
    WHERE is_primary = true;

CREATE TABLE recipe_likes
(
    user_id    UUID      NOT NULL REFERENCES users (id)   ON DELETE CASCADE,
    recipe_id  UUID      NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    created_at timestamp DEFAULT current_timestamp,
    PRIMARY KEY (user_id, recipe_id)    -- 同一用户对同一菜谱只能点一次赞
);

-- 高频查询是"这个菜谱有多少赞 / 谁赞了"，给 recipe_id 方向建索引
CREATE INDEX idx_likes_recipe ON recipe_likes (recipe_id);

CREATE TABLE embeddings
(
    id         uuid primary key default gen_random_uuid() not null,
    owner_type VARCHAR(32)                                NOT NULL, -- 'recipe' / 'ingredient'
    owner_id   UUID                                       NOT NULL, -- 指向 recipes.id 或 ingredients.id
    model      VARCHAR(128)                               NOT NULL, -- 哪个模型生成的,如 'text-embedding-3-large'
    vector     VECTOR(1024)                               NOT NULL,
    created_at timestamp        DEFAULT current_timestamp,
    check ( owner_type in ('recipe', 'ingredient')),
    UNIQUE (owner_type, owner_id, model)
);

create table activate_code
(
    activation_code varchar(255)                        not null primary key,
    create_time     timestamp default CURRENT_TIMESTAMP not null,
    used_time       timestamp                           null,
    expire_time     timestamp                           not null,
    is_used         boolean   default false             not null,
    user_id         uuid                                null references users (id),
    is_abandon      boolean   default false             not null
);