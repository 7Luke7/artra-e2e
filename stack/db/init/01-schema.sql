-- Artra database schema for the end-to-end test stack.
--
-- Captured from the application's own database with:
--   pg_dump --schema-only --no-owner --no-privileges
--
-- It lives here, rather than in the application repository, because Artra has
-- no migration tool: without a checked-in schema there is no way to stand the
-- application up from nothing, and a test environment that cannot be rebuilt
-- from scratch is not a test environment.
--
-- Postgres runs every file in /docker-entrypoint-initdb.d once, in name order,
-- when the data directory is empty. 02-seed.sql follows this one.

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';
CREATE TYPE public.contact_message_status AS ENUM (
    'unread',
    'read',
    'replied'
);
CREATE TABLE public."User" (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    email text NOT NULL,
    password text,
    email_verified boolean DEFAULT false,
    salt text,
    google_id text,
    avatar text,
    created_at timestamp without time zone DEFAULT now(),
    role text DEFAULT 'student'::text,
    CONSTRAINT "User_role_check" CHECK ((role = ANY (ARRAY['student'::text, 'instructor'::text, 'admin'::text])))
);
CREATE TABLE public.contact_message (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    email text NOT NULL,
    subject text NOT NULL,
    message text NOT NULL,
    status public.contact_message_status DEFAULT 'unread'::public.contact_message_status NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT contact_message_email_check CHECK ((email ~ '^[a-zA-Z0-9][a-zA-Z0-9._%+-]*@[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}$'::text)),
    CONSTRAINT contact_message_message_check CHECK (((length(message) >= 50) AND (length(message) <= 1000))),
    CONSTRAINT contact_message_name_check CHECK (((length(name) >= 2) AND (length(name) <= 100))),
    CONSTRAINT email_trimmed CHECK ((email = TRIM(BOTH FROM email)))
);
CREATE TABLE public.course (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    instructor_user_id uuid NOT NULL,
    title text NOT NULL,
    slug text NOT NULL,
    description text,
    thumbnail_url text,
    price numeric(10,2) DEFAULT 0,
    original_price numeric(10,2),
    status text DEFAULT 'draft'::text,
    level text DEFAULT 'begineer'::text,
    total_duration integer DEFAULT 0,
    total_lessons integer DEFAULT 0,
    review_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    preview_lesson_id uuid,
    enrollment_count integer DEFAULT 0,
    average_rating numeric(3,2) DEFAULT 0,
    category_id uuid,
    CONSTRAINT course_level_check CHECK ((level = ANY (ARRAY['beginner'::text, 'intermediate'::text, 'advanced'::text]))),
    CONSTRAINT course_status_check CHECK ((status = ANY (ARRAY['draft'::text, 'published'::text, 'archived'::text])))
);
CREATE TABLE public.course_category (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    parent_id uuid,
    name text NOT NULL,
    slug text NOT NULL,
    description text,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);
CREATE TABLE public.course_lesson (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    section_id uuid,
    course_id uuid,
    title text NOT NULL,
    video_url text NOT NULL,
    video_duration integer NOT NULL,
    lesson_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);
CREATE TABLE public.course_purchase (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    course_id uuid,
    user_id uuid NOT NULL,
    amount_paid numeric(10,2) NOT NULL,
    currency text DEFAULT 'GEL'::text,
    status text DEFAULT 'completed'::text,
    purchased_at timestamp without time zone DEFAULT now(),
    metadata jsonb DEFAULT '{}'::jsonb,
    CONSTRAINT course_purchase_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'completed'::text, 'refunded'::text])))
);
CREATE TABLE public.course_review (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    course_id uuid,
    user_id uuid NOT NULL,
    rating integer,
    comment text,
    created_at timestamp without time zone DEFAULT now(),
    CONSTRAINT course_review_rating_check CHECK (((rating >= 1) AND (rating <= 5)))
);
CREATE TABLE public.course_section (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    course_id uuid,
    title text NOT NULL,
    section_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    section_duration integer
);
CREATE TABLE public.enrollment (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    course_id uuid,
    user_id uuid NOT NULL,
    purchase_id uuid,
    enrolled_at timestamp without time zone DEFAULT now(),
    progress_percentage integer DEFAULT 0,
    last_lesson_id uuid,
    last_accessed_at timestamp without time zone
);
CREATE TABLE public.instructor_profile (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    bio text NOT NULL,
    headline text NOT NULL,
    specialization text NOT NULL,
    public_slug text NOT NULL,
    website_url text,
    social_links jsonb DEFAULT '{}'::jsonb,
    education jsonb DEFAULT '[]'::jsonb,
    work_experience jsonb DEFAULT '[]'::jsonb,
    total_students integer DEFAULT 0,
    total_courses integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);
CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    title text NOT NULL,
    description text,
    seen boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    notif_type text
);
CREATE TABLE public.payment_order (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    shop_order_id text NOT NULL,
    bog_order_id text,
    user_id uuid NOT NULL,
    course_id uuid NOT NULL,
    amount numeric(10,2) NOT NULL,
    status text DEFAULT 'pending'::text NOT NULL,
    payment_method text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone
);
CREATE TABLE public.user_devices (
    id uuid DEFAULT gen_random_uuid() CONSTRAINT user_devices_device_id_not_null NOT NULL,
    user_id uuid NOT NULL,
    user_agent text NOT NULL,
    ip_address text,
    browser character varying(50),
    browser_version character varying(50),
    os character varying(50),
    os_version character varying(50),
    device_type character varying(50),
    device_vendor character varying(50),
    device_model character varying(50),
    device_fingerprint character varying(255),
    status character varying(20) DEFAULT 'trusted'::character varying,
    last_used timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    session_id text,
    pending_verification_id text
);
ALTER TABLE ONLY public."User"
    ADD CONSTRAINT "User_email_key" UNIQUE (email);
ALTER TABLE ONLY public."User"
    ADD CONSTRAINT "User_google_id_key" UNIQUE (google_id);
ALTER TABLE ONLY public."User"
    ADD CONSTRAINT "User_pkey" PRIMARY KEY (id);
ALTER TABLE ONLY public.contact_message
    ADD CONSTRAINT contact_message_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course_category
    ADD CONSTRAINT course_category_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course_category
    ADD CONSTRAINT course_category_slug_key UNIQUE (slug);
ALTER TABLE ONLY public.course_lesson
    ADD CONSTRAINT course_lesson_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course_purchase
    ADD CONSTRAINT course_purchase_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course_review
    ADD CONSTRAINT course_review_course_id_user_id_key UNIQUE (course_id, user_id);
ALTER TABLE ONLY public.course_review
    ADD CONSTRAINT course_review_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course_section
    ADD CONSTRAINT course_section_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_slug_key UNIQUE (slug);
ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_course_id_user_id_key UNIQUE (course_id, user_id);
ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.instructor_profile
    ADD CONSTRAINT instructor_profile_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.instructor_profile
    ADD CONSTRAINT instructor_profile_public_slug_key UNIQUE (public_slug);
ALTER TABLE ONLY public.instructor_profile
    ADD CONSTRAINT instructor_profile_user_id_key UNIQUE (user_id);
ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_order
    ADD CONSTRAINT payment_order_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_order
    ADD CONSTRAINT payment_order_shop_order_id_key UNIQUE (shop_order_id);
ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT unq_user_id_fingerprint_user_devices UNIQUE (user_id, device_fingerprint);
ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT user_devices_pkey PRIMARY KEY (id);
CREATE INDEX contact_message_email_idx ON public.contact_message USING btree (email);
CREATE INDEX contact_message_status_idx ON public.contact_message USING btree (status);
CREATE INDEX idx_category_parent ON public.course_category USING btree (parent_id);
CREATE INDEX idx_category_slug ON public.course_category USING btree (slug);
CREATE INDEX idx_course_category ON public.course USING btree (category_id);
CREATE INDEX idx_course_created ON public.course USING btree (created_at DESC);
CREATE INDEX idx_course_instructor ON public.course USING btree (instructor_user_id);
CREATE INDEX idx_course_slug ON public.course USING btree (slug);
CREATE UNIQUE INDEX idx_course_slug_unique ON public.course USING btree (slug) WHERE (status = 'published'::text);
CREATE INDEX idx_course_status ON public.course USING btree (status) WHERE (status = 'published'::text);
CREATE INDEX idx_courses_cursor ON public.course USING btree (status, created_at DESC, id);
CREATE INDEX idx_enrollment_user_course ON public.enrollment USING btree (user_id, course_id);
CREATE INDEX idx_instructor_public_slug ON public.instructor_profile USING btree (public_slug);
CREATE INDEX idx_instructor_user ON public.instructor_profile USING btree (user_id);
CREATE INDEX idx_instructor_user_public ON public.instructor_profile USING btree (user_id, public_slug);
CREATE INDEX idx_lesson_course ON public.course_lesson USING btree (course_id);
CREATE INDEX idx_lesson_course_section ON public.course_lesson USING btree (course_id, section_id, lesson_order);
CREATE INDEX idx_lesson_section ON public.course_lesson USING btree (section_id, lesson_order);
CREATE INDEX idx_notifications_fetch ON public.notifications USING btree (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_user_created ON public.notifications USING btree (user_id, created_at DESC);
CREATE INDEX idx_notifications_user_id ON public.notifications USING btree (user_id);
CREATE INDEX idx_notifications_user_seen ON public.notifications USING btree (user_id, seen);
CREATE INDEX idx_purchase_course ON public.course_purchase USING btree (course_id);
CREATE INDEX idx_purchase_created ON public.course_purchase USING btree (purchased_at DESC);
CREATE INDEX idx_purchase_user ON public.course_purchase USING btree (user_id);
CREATE INDEX idx_purchase_user_created ON public.course_purchase USING btree (user_id, purchased_at DESC);
CREATE INDEX idx_review_course ON public.course_review USING btree (course_id);
CREATE INDEX idx_review_course_created ON public.course_review USING btree (course_id, created_at DESC);
CREATE INDEX idx_review_user ON public.course_review USING btree (user_id);
CREATE INDEX idx_section_course ON public.course_section USING btree (course_id, section_order);
CREATE INDEX idx_user_devices_user_id ON public.user_devices USING btree (user_id);
CREATE INDEX idx_user_email ON public."User" USING btree (email);
CREATE INDEX idx_user_role ON public."User" USING btree (role) WHERE (role = ANY (ARRAY['instructor'::text, 'admin'::text]));
CREATE INDEX payment_order_course_id_idx ON public.payment_order USING btree (course_id);
CREATE INDEX payment_order_status_idx ON public.payment_order USING btree (status);
CREATE INDEX payment_order_user_id_idx ON public.payment_order USING btree (user_id);
ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.course_category(id) ON DELETE SET NULL;
ALTER TABLE ONLY public.course_category
    ADD CONSTRAINT course_category_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.course_category(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_instructor_user_id_fkey FOREIGN KEY (instructor_user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course_lesson
    ADD CONSTRAINT course_lesson_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course_lesson
    ADD CONSTRAINT course_lesson_section_id_fkey FOREIGN KEY (section_id) REFERENCES public.course_section(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_preview_lesson_id_fkey FOREIGN KEY (preview_lesson_id) REFERENCES public.course_lesson(id) ON DELETE SET NULL;
ALTER TABLE ONLY public.course_purchase
    ADD CONSTRAINT course_purchase_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course_purchase
    ADD CONSTRAINT course_purchase_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course_review
    ADD CONSTRAINT course_review_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course_review
    ADD CONSTRAINT course_review_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.course_section
    ADD CONSTRAINT course_section_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_last_lesson_id_fkey FOREIGN KEY (last_lesson_id) REFERENCES public.course_lesson(id) ON DELETE SET NULL;
ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_purchase_id_fkey FOREIGN KEY (purchase_id) REFERENCES public.course_purchase(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.instructor_profile
    ADD CONSTRAINT instructor_profile_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE NOT VALID;
ALTER TABLE ONLY public.payment_order
    ADD CONSTRAINT payment_order_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.course(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.payment_order
    ADD CONSTRAINT payment_order_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.user_devices
    ADD CONSTRAINT user_devices_user_id_fkey FOREIGN KEY (user_id) REFERENCES public."User"(id) ON DELETE CASCADE;
