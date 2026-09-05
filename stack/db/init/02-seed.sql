-- Deterministic fixture data for the end-to-end suite.
--
-- Everything here is a fixed backdrop: categories, two instructors, a
-- catalogue, and the accounts the tests sign in against. Every id is a
-- name-derived UUIDv5 rather than gen_random_uuid(), so the same row has the
-- same id on every machine and a failure can be traced to an exact record.
--
-- What is deliberately NOT here: anything a test creates for itself. Accounts,
-- contact messages and sessions are made through the UI by the tests that are
-- about them, and cleaned up by those tests - a fixture that pre-creates the
-- thing under test is how a suite ends up green against a broken feature.
--
-- Passwords are NOT set here. Artra hashes with Argon2id keyed by ARGON_SECRET,
-- which Postgres cannot compute; stack/app/seed-users.mjs fills them in on
-- every start (see the 'seed' service in docker-compose.yml).

BEGIN;

-- Categories ----------------------------------------------------------------
INSERT INTO course_category (id, name, slug, description, sort_order) VALUES
  ('59306a75-74c9-5604-83a1-d63ae6419f4a', 'მშენებლობა', 'construction', 'მშენებლობა - პრაქტიკული კურსები', 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_category (id, name, slug, description, sort_order) VALUES
  ('bcfec4e3-5331-548c-966c-22cca08bd36e', 'ტექნოლოგიები', 'technology', 'ტექნოლოგიები - პრაქტიკული კურსები', 2)
ON CONFLICT (id) DO NOTHING;

-- People --------------------------------------------------------------------
-- email_verified is true so these accounts behave like ones that completed
-- the signup flow; password and salt are filled in by seed-users.mjs.
INSERT INTO "User" (id, name, email, email_verified, role, created_at) VALUES
  ('9e258bb8-4a80-500c-bccd-aee6c1557316', 'ნინო ბერიძე', 'nino.beridze@artra.test', true, 'instructor', now() - interval '400 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO "User" (id, name, email, email_verified, role, created_at) VALUES
  ('5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'გიორგი კაპანაძე', 'giorgi.kapanadze@artra.test', true, 'instructor', now() - interval '400 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO "User" (id, name, email, email_verified, role, created_at) VALUES
  ('de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 'თამარ ცქიტიშვილი', 'student@artra.test', true, 'student', now() - interval '300 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO "User" (id, name, email, email_verified, role, created_at) VALUES
  ('a473b533-47d2-5ee6-8b76-cfbf1993d965', 'ლევან მაისურაძე', 'reviewer@artra.test', true, 'student', now() - interval '280 days')
ON CONFLICT (id) DO NOTHING;

-- Instructor profiles ---------------------------------------------------------
INSERT INTO instructor_profile (id, user_id, bio, headline, specialization, public_slug, total_students, total_courses) VALUES
  ('ec968570-7a89-5073-9864-8c1a3638d0d2', '9e258bb8-4a80-500c-bccd-aee6c1557316', 'ვმუშაობ სამოქალაქო ინჟინრად 12 წელია და ვასწავლი პრაქტიკულ პროექტირებას რეალურ ობიექტებზე დაყრდნობით.', 'სამოქალაქო ინჟინერი, 12 წლის გამოცდილება', 'მშენებლობა და პროექტირება', 'nino-beridze', 1840, 9)
ON CONFLICT (id) DO NOTHING;
INSERT INTO instructor_profile (id, user_id, bio, headline, specialization, public_slug, total_students, total_courses) VALUES
  ('13bf8209-1e6d-5a09-9fac-ab3fdfab8318', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'ვქმნი ვებ-პროდუქტებს ათი წელია და ვასწავლი იმას, რაც რეალურ სამუშაოში მართლა გამოიყენება.', 'სენიორ დეველოპერი, პროდუქტ ინჟინერი', 'ვებ-დეველოპმენტი', 'giorgi-kapanadze', 1840, 9)
ON CONFLICT (id) DO NOTHING;

-- Catalogue -------------------------------------------------------------------
-- 18 published courses: more than one page (the list is served 15 at a time),
-- spread across both categories and all three levels, with distinct prices so
-- an ordering assertion has a total order to check against.
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('7dbeb3d9-64bf-5667-8462-ab6b12812a32', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'ბეტონის კონსტრუქციების პროექტირება', 'betonis-konstruqciebis-proeqtireba',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 499.00, 699.00, 'published', 'advanced',
   7200, 6, 412, 4.80, 2,
   now() - interval '1 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('df555fc6-b836-5ccd-8e93-ebdffe242ab9', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'სამშენებლო ხარჯთაღრიცხვა პრაქტიკაში', 'samsheneblo-xarjtagricxva',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 349.00, NULL, 'published', 'intermediate',
   11400, 7, 231, 4.30, 0,
   now() - interval '2 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'AutoCAD დამწყებთათვის', 'autocad-damwyebtatvis',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 129.00, 199.00, 'published', 'beginner',
   15600, 8, 876, 4.90, 0,
   now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('5679f863-e889-5b6a-900e-dd0d51fdf569', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'სამშენებლო უსაფრთხოების სტანდარტები', 'samsheneblo-usafrtxoeba',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 59.00, NULL, 'published', 'beginner',
   19800, 9, 154, 4.10, 2,
   now() - interval '4 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('3847c3fb-444d-5cea-96db-3b2729e77098', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'რევიტი არქიტექტორებისთვის', 'reviti-arqiteqtorebistvis',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 279.00, NULL, 'published', 'intermediate',
   24000, 10, 320, 4.60, 0,
   now() - interval '5 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('ca43deeb-45d6-5e97-8eb9-a3403b5e4894', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'გეოდეზია სამშენებლო ობიექტზე', 'geodezia-obieqtze',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 189.00, NULL, 'published', 'intermediate',
   28200, 6, 98, 3.90, 0,
   now() - interval '6 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('d9bd55f2-7662-5190-b74f-4b3ef966302f', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'სახურავის სისტემები და ჰიდროიზოლაცია', 'saxuravis-sistemebi',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 439.00, NULL, 'published', 'advanced',
   10800, 7, 187, 4.40, 2,
   now() - interval '7 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('937a44c1-1780-5345-a7f2-8df7bb5a302a', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'ინტერიერის დიზაინის საფუძვლები', 'interieris-dizaini',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 89.00, NULL, 'published', 'beginner',
   10800, 8, 540, 4.70, 0,
   now() - interval '8 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('357fa79d-6c4c-56b8-974d-f4076fc64cac', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'სამშენებლო პროექტის მართვა', 'proeqtis-martva',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 399.00, NULL, 'published', 'advanced',
   15000, 9, 265, 4.20, 0,
   now() - interval '9 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('2cdb823b-f282-5d3c-aecb-325f1e803e86', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'ენერგოეფექტური მშენებლობა', 'energoefeqturi-mshenebloba',
   'პრაქტიკული კურსი რეალურ სამშენებლო ობიექტებზე დაფუძნებული მაგალითებით. თითოეული მოდული სრულდება დამოუკიდებელი დავალებით.',
   '/course-image.jpg', 239.00, NULL, 'published', 'intermediate',
   19200, 10, 143, 4.50, 2,
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('de5a3ffa-d75a-5622-8967-2a0d38e43f2b', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'JavaScript ნულიდან', 'javascript-nulidan',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 99.00, 149.00, 'published', 'beginner',
   23400, 6, 1204, 4.95, 0,
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('99e676e1-20df-5b27-8f68-bd9dedcfa95c', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'React-ის პრაქტიკული კურსი', 'react-praqtikuli',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 149.00, NULL, 'published', 'intermediate',
   27600, 7, 731, 4.65, 0,
   now() - interval '12 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('a207f124-6cfd-5603-9b56-d5ec17e0375b', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'Node.js და REST API', 'nodejs-rest-api',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 199.00, NULL, 'published', 'intermediate',
   10200, 8, 455, 4.35, 2,
   now() - interval '13 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('f468b8a5-641f-556b-ba49-e9ecec4a1f92', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'SQL და მონაცემთა ბაზები', 'sql-monacemta-bazebi',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 69.00, 99.00, 'published', 'beginner',
   14400, 9, 612, 4.05, 0,
   now() - interval '14 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'Git და გუნდური მუშაობა', 'git-gunduri-musaoba',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 39.00, NULL, 'published', 'beginner',
   14400, 10, 988, 4.85, 0,
   now() - interval '15 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('46725cbb-8270-5785-af01-3dc81d84f16a', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'ავტომატიზებული ტესტირება', 'avtomatizebuli-testireba',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 329.00, 449.00, 'published', 'advanced',
   18600, 6, 276, 4.55, 2,
   now() - interval '16 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('d4a0b98f-e2cb-542f-ac73-99b272d85ac6', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'Docker დეველოპერებისთვის', 'docker-developerebistvis',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 219.00, NULL, 'published', 'intermediate',
   22800, 7, 389, 4.25, 0,
   now() - interval '17 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, original_price, status, level, total_duration, total_lessons, enrollment_count, average_rating, review_count, created_at) VALUES
  ('93c0f27c-88af-5cdb-9529-7e6040366c1c', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'კიბერუსაფრთხოების საფუძვლები', 'kiberusafrtxoeba',
   'პრაქტიკული კურსი რეალური პროექტების მაგალითებზე. ყოველი მოდულის ბოლოს დამოუკიდებელი დავალება და კოდის მიმოხილვა.',
   '/course-image.jpg', 259.00, NULL, 'published', 'advanced',
   27000, 8, 164, 3.95, 0,
   now() - interval '18 days')
ON CONFLICT (id) DO NOTHING;

-- Drafts ----------------------------------------------------------------------
-- Present so the catalogue is proved to filter on status rather than simply
-- listing every row in the table.
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, status, level, total_duration, total_lessons, created_at) VALUES
  ('66a3ea80-dce8-51b5-bd38-87ffc9076bd7', '9e258bb8-4a80-500c-bccd-aee6c1557316', '59306a75-74c9-5604-83a1-d63ae6419f4a', 'დრაფტი: ხის კონსტრუქციები', 'draft-course-1',
   'ჯერ არ არის გამოქვეყნებული.', '/course-image.jpg', 149.00, 'draft', 'beginner', 3600, 4,
   now() - interval '30 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course (id, instructor_user_id, category_id, title, slug, description, thumbnail_url, price, status, level, total_duration, total_lessons, created_at) VALUES
  ('e1a1d146-9337-52a9-81bb-e5776a713e74', '5f61886f-3b8b-5e7c-8069-ca61f5329e41', 'bcfec4e3-5331-548c-966c-22cca08bd36e', 'დრაფტი: Kubernetes', 'draft-course-2',
   'ჯერ არ არის გამოქვეყნებული.', '/course-image.jpg', 149.00, 'draft', 'advanced', 3600, 4,
   now() - interval '31 days')
ON CONFLICT (id) DO NOTHING;

-- Sections and lessons ---------------------------------------------------------
-- Two sections per course, three lessons each, so the curriculum on the course
-- page has something real to expand. The first lesson of each course is its
-- free preview.
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('8eb4ace3-527d-5771-8d19-92530ce100c6', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('66440692-c6a8-5b0f-b6f0-6b4caa53cb3d', '8eb4ace3-527d-5771-8d19-92530ce100c6', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('1df99ac3-e193-5a16-8410-1c7373e63f0c', '8eb4ace3-527d-5771-8d19-92530ce100c6', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('5f957bbe-5a87-5005-a9b3-04df4a961ec5', '8eb4ace3-527d-5771-8d19-92530ce100c6', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('777fc8ee-2e66-528e-bc2e-2c530be9b9a0', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('63305420-2ec7-5006-8159-1e18b625d5a9', '777fc8ee-2e66-528e-bc2e-2c530be9b9a0', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('1d833c85-27a7-5459-8e5e-6e51dc53b2ee', '777fc8ee-2e66-528e-bc2e-2c530be9b9a0', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('cdc7ff23-4df6-5a08-bb91-14fa43bc3d09', '777fc8ee-2e66-528e-bc2e-2c530be9b9a0', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '66440692-c6a8-5b0f-b6f0-6b4caa53cb3d' WHERE id = '7dbeb3d9-64bf-5667-8462-ab6b12812a32';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('4caa4976-3c8c-5f6d-9e6b-5ff1a16b1a4c', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('e23f024f-1a28-5063-b501-2ab77392307b', '4caa4976-3c8c-5f6d-9e6b-5ff1a16b1a4c', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('733aeaf5-c6e2-5a50-8a90-c3c050be26a9', '4caa4976-3c8c-5f6d-9e6b-5ff1a16b1a4c', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('72661b54-a2ad-5eff-9429-27ba6c7bee5d', '4caa4976-3c8c-5f6d-9e6b-5ff1a16b1a4c', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('a88724c0-2cfc-5220-9501-311826b44309', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('ce3ca78b-a4b7-5292-a029-76625271c111', 'a88724c0-2cfc-5220-9501-311826b44309', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('75362832-b896-5ea7-bce4-c3bbcce99e1e', 'a88724c0-2cfc-5220-9501-311826b44309', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('a0c015a7-fa43-5afb-930f-64edee5aec28', 'a88724c0-2cfc-5220-9501-311826b44309', 'df555fc6-b836-5ccd-8e93-ebdffe242ab9', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'e23f024f-1a28-5063-b501-2ab77392307b' WHERE id = 'df555fc6-b836-5ccd-8e93-ebdffe242ab9';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('fe4a8f31-2c94-54fb-a69c-a1f412ee64ee', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('e686520e-d7aa-5953-9c41-da540368b23a', 'fe4a8f31-2c94-54fb-a69c-a1f412ee64ee', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('89429c47-3760-53fb-8a27-6cddeb6b6e1b', 'fe4a8f31-2c94-54fb-a69c-a1f412ee64ee', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('a4f2b618-3baf-5133-875b-2d81737196e8', 'fe4a8f31-2c94-54fb-a69c-a1f412ee64ee', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('16a0629b-6ee1-538b-afd5-627993dde33b', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('6987021a-839b-5f6b-a8e9-10da62b9b564', '16a0629b-6ee1-538b-afd5-627993dde33b', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('779e54f4-4550-51f4-a6cc-376ae0c35a14', '16a0629b-6ee1-538b-afd5-627993dde33b', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('5443de85-bf8c-52c2-aa28-146adcaea068', '16a0629b-6ee1-538b-afd5-627993dde33b', '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'e686520e-d7aa-5953-9c41-da540368b23a' WHERE id = '0cea2b3b-96ba-5a7a-8bcc-b4c6f9b420f1';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('2a759825-bab1-541a-ada6-cb8173c8c934', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('15daf2e2-2966-57ad-8a8f-09dd9152e5ad', '2a759825-bab1-541a-ada6-cb8173c8c934', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('45199dd9-d643-5679-b507-b3a5dca0fc0b', '2a759825-bab1-541a-ada6-cb8173c8c934', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('def4e494-62ee-5cc8-a90f-2ecbe826511b', '2a759825-bab1-541a-ada6-cb8173c8c934', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('a9769450-807d-51a1-a91b-bff8d036726b', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('33e996c0-4b2f-5c0e-bea2-2da9d7fec96c', 'a9769450-807d-51a1-a91b-bff8d036726b', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('c04b44a3-61fb-549f-8fa9-03c04aa66a7c', 'a9769450-807d-51a1-a91b-bff8d036726b', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('cfaf34f2-43ce-5f24-82cd-a5b5ccedaf6f', 'a9769450-807d-51a1-a91b-bff8d036726b', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '15daf2e2-2966-57ad-8a8f-09dd9152e5ad' WHERE id = '5679f863-e889-5b6a-900e-dd0d51fdf569';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('909da76e-5f00-587a-8a31-011d6a96f93a', '3847c3fb-444d-5cea-96db-3b2729e77098', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d7827bc8-dfdd-5807-8e68-7fb0568f20d0', '909da76e-5f00-587a-8a31-011d6a96f93a', '3847c3fb-444d-5cea-96db-3b2729e77098', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('58ea5102-8088-5288-9a09-c58e06f17ff1', '909da76e-5f00-587a-8a31-011d6a96f93a', '3847c3fb-444d-5cea-96db-3b2729e77098', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('6b710d2d-ab78-58c1-83eb-59f7e61fa723', '909da76e-5f00-587a-8a31-011d6a96f93a', '3847c3fb-444d-5cea-96db-3b2729e77098', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('7c9ee6fd-7d63-5ba0-8109-3d22915e6084', '3847c3fb-444d-5cea-96db-3b2729e77098', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('7a09e3fb-bb64-5a22-93b4-b7950c069d67', '7c9ee6fd-7d63-5ba0-8109-3d22915e6084', '3847c3fb-444d-5cea-96db-3b2729e77098', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('1de7d9d9-9672-5781-9fe9-7e9a5936d353', '7c9ee6fd-7d63-5ba0-8109-3d22915e6084', '3847c3fb-444d-5cea-96db-3b2729e77098', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('c2aea608-10f8-5c38-a6ae-a2756e1b574b', '7c9ee6fd-7d63-5ba0-8109-3d22915e6084', '3847c3fb-444d-5cea-96db-3b2729e77098', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'd7827bc8-dfdd-5807-8e68-7fb0568f20d0' WHERE id = '3847c3fb-444d-5cea-96db-3b2729e77098';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('a69f146f-741f-550c-9d37-6695156732ff', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('b3e2f851-239d-5656-9f7c-f564246b39d8', 'a69f146f-741f-550c-9d37-6695156732ff', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('e58d3dba-e25b-5452-b44f-cc34d25bccc9', 'a69f146f-741f-550c-9d37-6695156732ff', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('5d079462-9d7c-5ff4-8ad8-b98efea8b43d', 'a69f146f-741f-550c-9d37-6695156732ff', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('3969cab5-23af-5c33-bea8-8ab820b540de', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('55628ee7-f1a1-51e9-9ba5-763d62877201', '3969cab5-23af-5c33-bea8-8ab820b540de', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d7c41fa3-36a6-5bbe-8e8e-43030a70c3e3', '3969cab5-23af-5c33-bea8-8ab820b540de', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('c57effc9-8c05-5d53-8508-7ba2622a8284', '3969cab5-23af-5c33-bea8-8ab820b540de', 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'b3e2f851-239d-5656-9f7c-f564246b39d8' WHERE id = 'ca43deeb-45d6-5e97-8eb9-a3403b5e4894';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('97301700-263a-5e92-b810-569bca587d67', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('883d41be-b3a1-59db-8694-92e47eeabb79', '97301700-263a-5e92-b810-569bca587d67', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('3c9c8b0e-4909-5076-b8fe-3a175011d419', '97301700-263a-5e92-b810-569bca587d67', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d697531b-e9b2-51b6-8f46-d8562d56ff9b', '97301700-263a-5e92-b810-569bca587d67', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('c6ddc786-d2ba-5187-a7e5-92c308111ff0', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('42aa30f8-db90-5a70-a6d4-44b5485c3ef9', 'c6ddc786-d2ba-5187-a7e5-92c308111ff0', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('1b58a554-0900-5921-b2f5-dae08194db2b', 'c6ddc786-d2ba-5187-a7e5-92c308111ff0', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('852564c4-7380-5c50-a3b4-715a4cbb7398', 'c6ddc786-d2ba-5187-a7e5-92c308111ff0', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '883d41be-b3a1-59db-8694-92e47eeabb79' WHERE id = 'd9bd55f2-7662-5190-b74f-4b3ef966302f';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('efd9ff6e-2bea-552f-896f-b8721529f503', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('514440ae-643d-5eb8-a013-c8afc2ce5cb8', 'efd9ff6e-2bea-552f-896f-b8721529f503', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('2c7f3f01-11f3-5d95-beb8-26df88a401fe', 'efd9ff6e-2bea-552f-896f-b8721529f503', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('5700a00a-31f5-5b83-bbc1-b475d1a41290', 'efd9ff6e-2bea-552f-896f-b8721529f503', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('67fb28e8-0a04-5b53-90d4-ca725061be7f', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('09bdd18d-82e6-5e67-bcb3-432d1d168702', '67fb28e8-0a04-5b53-90d4-ca725061be7f', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('533498dd-1a65-564e-a416-b551b1b88d15', '67fb28e8-0a04-5b53-90d4-ca725061be7f', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('c838dba8-3313-569b-b049-0de211ff77db', '67fb28e8-0a04-5b53-90d4-ca725061be7f', '937a44c1-1780-5345-a7f2-8df7bb5a302a', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '514440ae-643d-5eb8-a013-c8afc2ce5cb8' WHERE id = '937a44c1-1780-5345-a7f2-8df7bb5a302a';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('9c535074-d1e1-580a-9f55-a98229a4f91a', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('b09e1e72-4951-5a2d-846a-aab7b46fc2b1', '9c535074-d1e1-580a-9f55-a98229a4f91a', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('9e9a81cc-8546-5473-b131-47b94016f33f', '9c535074-d1e1-580a-9f55-a98229a4f91a', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('5e5b9435-46e4-5372-9519-ec1c3c1261cb', '9c535074-d1e1-580a-9f55-a98229a4f91a', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('50522875-83d3-5820-8d93-2e6b6609e4be', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('7734dd10-3ed4-5e9a-aa36-acd1bdfe1394', '50522875-83d3-5820-8d93-2e6b6609e4be', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('b9576747-fa98-53c1-8599-c2d5d6508e0e', '50522875-83d3-5820-8d93-2e6b6609e4be', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('090e5e27-d5fb-52fd-9154-143009b8c8ed', '50522875-83d3-5820-8d93-2e6b6609e4be', '357fa79d-6c4c-56b8-974d-f4076fc64cac', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'b09e1e72-4951-5a2d-846a-aab7b46fc2b1' WHERE id = '357fa79d-6c4c-56b8-974d-f4076fc64cac';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('ebbbf38e-67d1-5354-abea-3eda68083e95', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('5846629e-808c-54f3-9595-9fe5aa99b461', 'ebbbf38e-67d1-5354-abea-3eda68083e95', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('1cd321bb-97fb-58a1-ae8a-e6b7d047e080', 'ebbbf38e-67d1-5354-abea-3eda68083e95', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('0a598264-18af-5f4f-af80-a9f856859e53', 'ebbbf38e-67d1-5354-abea-3eda68083e95', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('b14d43c1-af08-541f-8665-2aba506ed789', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('18bf4b4d-c4b5-5bff-8fd0-0dd132f4713c', 'b14d43c1-af08-541f-8665-2aba506ed789', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('8ec3153c-6231-5b38-9306-3a659e9af5a1', 'b14d43c1-af08-541f-8665-2aba506ed789', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('b716d29f-a487-5a15-bae9-1c19d437997f', 'b14d43c1-af08-541f-8665-2aba506ed789', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '5846629e-808c-54f3-9595-9fe5aa99b461' WHERE id = '2cdb823b-f282-5d3c-aecb-325f1e803e86';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('5819485d-a863-51e1-8dbc-e8b4b213f60a', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d2080f10-c74f-5b12-835d-3ffe1219e542', '5819485d-a863-51e1-8dbc-e8b4b213f60a', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('e4e320ef-4f5a-5858-ba3c-b24d6dfee7b6', '5819485d-a863-51e1-8dbc-e8b4b213f60a', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('c90e68c8-64f3-5e14-a6be-37ceeed5e788', '5819485d-a863-51e1-8dbc-e8b4b213f60a', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('5648d211-b37b-5e05-a20c-17022ade003c', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('24a0cbd0-a326-5a95-9f56-85351126808a', '5648d211-b37b-5e05-a20c-17022ade003c', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d427c4b5-d1ad-5043-bf55-645ec7fbfea4', '5648d211-b37b-5e05-a20c-17022ade003c', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('807e3aaf-7b8f-5595-8934-3641949da1f3', '5648d211-b37b-5e05-a20c-17022ade003c', 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'd2080f10-c74f-5b12-835d-3ffe1219e542' WHERE id = 'de5a3ffa-d75a-5622-8967-2a0d38e43f2b';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('07c50d11-5182-538b-bfd6-63de6b748e82', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('15a589ad-0703-5ccd-8f10-b2396b914030', '07c50d11-5182-538b-bfd6-63de6b748e82', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('31691786-dd30-58fa-a73e-0cd35e324d1b', '07c50d11-5182-538b-bfd6-63de6b748e82', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('e77e0ae3-7e20-5f49-91ea-a96f09c43c04', '07c50d11-5182-538b-bfd6-63de6b748e82', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('92dd799d-b1e2-5b7f-8ccd-7e3f512beecb', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('f9caa2f8-3a90-5e71-93d1-252837050321', '92dd799d-b1e2-5b7f-8ccd-7e3f512beecb', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('004bffa3-1a6a-5a6d-9135-ad4f2a1f62b6', '92dd799d-b1e2-5b7f-8ccd-7e3f512beecb', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('2eac5bd5-bc46-5877-85f2-3222d8299b14', '92dd799d-b1e2-5b7f-8ccd-7e3f512beecb', '99e676e1-20df-5b27-8f68-bd9dedcfa95c', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '15a589ad-0703-5ccd-8f10-b2396b914030' WHERE id = '99e676e1-20df-5b27-8f68-bd9dedcfa95c';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('69a721b0-2f54-518c-a042-9d84b2d528b7', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('8d7c9955-1b66-5d5d-912c-6520f4983003', '69a721b0-2f54-518c-a042-9d84b2d528b7', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('ae80af24-f5b4-5a1c-92ac-2cd3b190a1e0', '69a721b0-2f54-518c-a042-9d84b2d528b7', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('a913f646-5d21-53b3-b3fb-41215cc746a2', '69a721b0-2f54-518c-a042-9d84b2d528b7', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('657f046e-3868-551e-b61b-afa1171a6095', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('32de3774-7f5d-5bc1-9664-ae325beee0e5', '657f046e-3868-551e-b61b-afa1171a6095', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('fe82511f-5b32-5201-84ea-e531f3a1687e', '657f046e-3868-551e-b61b-afa1171a6095', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('6d8c1625-7d65-5ff0-bccb-1081d1246e95', '657f046e-3868-551e-b61b-afa1171a6095', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '8d7c9955-1b66-5d5d-912c-6520f4983003' WHERE id = 'a207f124-6cfd-5603-9b56-d5ec17e0375b';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('c39feed1-a56d-58a4-abfb-263f74173c85', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('4886a462-646e-527e-b65a-278c33f7a49d', 'c39feed1-a56d-58a4-abfb-263f74173c85', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('f674bb1a-cc46-58f8-8b9c-51746150e9b5', 'c39feed1-a56d-58a4-abfb-263f74173c85', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('956dcd06-c7eb-5dfc-bc7a-3163caacf073', 'c39feed1-a56d-58a4-abfb-263f74173c85', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('863f2894-b89b-58d7-a391-2816aec88ba6', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('76fd092a-4d19-594e-8f75-a7fbb95d35c2', '863f2894-b89b-58d7-a391-2816aec88ba6', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('b66242d7-e0cd-589f-a33b-529964fee47e', '863f2894-b89b-58d7-a391-2816aec88ba6', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('674633e3-ae80-5611-b11a-27b2d666cb99', '863f2894-b89b-58d7-a391-2816aec88ba6', 'f468b8a5-641f-556b-ba49-e9ecec4a1f92', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '4886a462-646e-527e-b65a-278c33f7a49d' WHERE id = 'f468b8a5-641f-556b-ba49-e9ecec4a1f92';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('050d6808-1c38-533d-8d4d-0b4abb95f9ba', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('c2c706a9-2507-5806-9ea3-2010d33fb289', '050d6808-1c38-533d-8d4d-0b4abb95f9ba', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('f8e613b9-ecae-5da3-b79d-90e8e585e28b', '050d6808-1c38-533d-8d4d-0b4abb95f9ba', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('920e5642-428a-538b-82b3-c5a7db529f6f', '050d6808-1c38-533d-8d4d-0b4abb95f9ba', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('e307a7a2-0924-5b3f-a28b-9a86bdbba8c0', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('598b8fd8-d820-568c-b42f-fe756827defd', 'e307a7a2-0924-5b3f-a28b-9a86bdbba8c0', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('44e60c50-b57a-5d7c-aebe-0f01e34b9358', 'e307a7a2-0924-5b3f-a28b-9a86bdbba8c0', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d9a30ca3-2aeb-54ed-80e0-aa720e8fe915', 'e307a7a2-0924-5b3f-a28b-9a86bdbba8c0', 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = 'c2c706a9-2507-5806-9ea3-2010d33fb289' WHERE id = 'ef73b8ed-6260-5c4f-83b2-a599eaa65f3d';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('2c300f76-2861-54cd-bc5a-b8c063ddfa33', '46725cbb-8270-5785-af01-3dc81d84f16a', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('53176f3e-2a23-5a29-8ba5-0e3791056499', '2c300f76-2861-54cd-bc5a-b8c063ddfa33', '46725cbb-8270-5785-af01-3dc81d84f16a', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('b5eb59d3-69a3-50ef-a6f0-39a312cd98f2', '2c300f76-2861-54cd-bc5a-b8c063ddfa33', '46725cbb-8270-5785-af01-3dc81d84f16a', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('12c6128f-d7dc-5a86-88b1-44ae50459849', '2c300f76-2861-54cd-bc5a-b8c063ddfa33', '46725cbb-8270-5785-af01-3dc81d84f16a', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('85a5e169-4ae9-5306-9e67-4ae35c7680ac', '46725cbb-8270-5785-af01-3dc81d84f16a', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('ec4e128d-c675-577f-9af2-e189916763e0', '85a5e169-4ae9-5306-9e67-4ae35c7680ac', '46725cbb-8270-5785-af01-3dc81d84f16a', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('d00df22b-c324-50b3-999a-2f84566981b0', '85a5e169-4ae9-5306-9e67-4ae35c7680ac', '46725cbb-8270-5785-af01-3dc81d84f16a', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('1709c25a-3682-5524-99b6-19c60d340281', '85a5e169-4ae9-5306-9e67-4ae35c7680ac', '46725cbb-8270-5785-af01-3dc81d84f16a', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '53176f3e-2a23-5a29-8ba5-0e3791056499' WHERE id = '46725cbb-8270-5785-af01-3dc81d84f16a';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('e73e28c6-d545-59a2-a281-eec3720d865c', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('9b19ac04-7188-544a-aa96-0dd1f9f6e768', 'e73e28c6-d545-59a2-a281-eec3720d865c', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('8bf6f2a2-816b-52f3-ad2b-253ff5cc1c15', 'e73e28c6-d545-59a2-a281-eec3720d865c', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('593b8e8e-4e06-5896-85cb-a7e83b2bae32', 'e73e28c6-d545-59a2-a281-eec3720d865c', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('d5aedfef-fbf3-5815-84b7-c1e1e9c5caee', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('4498a140-85c7-5650-84ff-f3bf3e2683b3', 'd5aedfef-fbf3-5815-84b7-c1e1e9c5caee', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('23b8d759-4080-5df3-b7d2-decf33cf133f', 'd5aedfef-fbf3-5815-84b7-c1e1e9c5caee', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('0158253a-f961-545b-ac2d-0c77cfbeb0fb', 'd5aedfef-fbf3-5815-84b7-c1e1e9c5caee', 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '9b19ac04-7188-544a-aa96-0dd1f9f6e768' WHERE id = 'd4a0b98f-e2cb-542f-ac73-99b272d85ac6';
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('6c0beaaf-d181-5a0b-9918-e4a62e8a2e8b', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'შესავალი და ინსტრუმენტები', 0, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('12697be2-767c-5d6d-9932-d4d2643c5352', '6c0beaaf-d181-5a0b-9918-e4a62e8a2e8b', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('654f3d51-cced-5cbe-b6d1-9a106f84a762', '6c0beaaf-d181-5a0b-9918-e4a62e8a2e8b', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('7cee3406-0b2d-5cb4-aa04-9aea38965f32', '6c0beaaf-d181-5a0b-9918-e4a62e8a2e8b', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_section (id, course_id, title, section_order, section_duration) VALUES
  ('0573aec6-f1ff-5585-8175-8034ee0e2181', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'პრაქტიკული მოდული', 1, 3600)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('7465bdd7-059b-5554-9cc3-4694a41df367', '0573aec6-f1ff-5585-8175-8034ee0e2181', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'გაკვეთილი 1', '/media/lesson.mp4', 600, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('0d55194d-8744-5dbf-9ac9-54a5782e67ab', '0573aec6-f1ff-5585-8175-8034ee0e2181', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'გაკვეთილი 2', '/media/lesson.mp4', 720, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_lesson (id, section_id, course_id, title, video_url, video_duration, lesson_order) VALUES
  ('bebe418f-bc97-528b-a0a2-90932b7ba10b', '0573aec6-f1ff-5585-8175-8034ee0e2181', '93c0f27c-88af-5cdb-9529-7e6040366c1c', 'გაკვეთილი 3', '/media/lesson.mp4', 840, 2)
ON CONFLICT (id) DO NOTHING;
UPDATE course SET preview_lesson_id = '12697be2-767c-5d6d-9932-d4d2643c5352' WHERE id = '93c0f27c-88af-5cdb-9529-7e6040366c1c';

-- Reviews ----------------------------------------------------------------------
-- Only on every third course, matching the review_count set above, so the
-- denormalised counter and the rows it summarises agree.
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('739d9c0a-af1c-5d51-b54a-79ac233f24bf', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 5, 'ძალიან პრაქტიკული კურსი, რეალურ სამუშაოში მაშინვე გამომადგა.',
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('c762f856-c9cb-5dfd-9ef2-cc753d0b4d05', '7dbeb3d9-64bf-5667-8462-ab6b12812a32', 'a473b533-47d2-5ee6-8b76-cfbf1993d965', 4, 'კარგად სტრუქტურირებული მასალა და გასაგები ახსნა.',
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('e5287c50-9e8d-5f92-a898-9bfcbe4b9ba8', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 5, 'ძალიან პრაქტიკული კურსი, რეალურ სამუშაოში მაშინვე გამომადგა.',
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('d20916c9-fc6f-508e-bad9-4a94a7743e96', '5679f863-e889-5b6a-900e-dd0d51fdf569', 'a473b533-47d2-5ee6-8b76-cfbf1993d965', 4, 'კარგად სტრუქტურირებული მასალა და გასაგები ახსნა.',
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('84b8c345-4df2-5570-84dd-5957a185c8ed', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 5, 'ძალიან პრაქტიკული კურსი, რეალურ სამუშაოში მაშინვე გამომადგა.',
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('8432c013-ac8c-51d7-a403-83f196da1c09', 'd9bd55f2-7662-5190-b74f-4b3ef966302f', 'a473b533-47d2-5ee6-8b76-cfbf1993d965', 4, 'კარგად სტრუქტურირებული მასალა და გასაგები ახსნა.',
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('cd919aab-0990-552e-b3b0-9bd40dd73205', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 5, 'ძალიან პრაქტიკული კურსი, რეალურ სამუშაოში მაშინვე გამომადგა.',
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('5757af5c-d162-59ab-8c6c-c8028116d28a', '2cdb823b-f282-5d3c-aecb-325f1e803e86', 'a473b533-47d2-5ee6-8b76-cfbf1993d965', 4, 'კარგად სტრუქტურირებული მასალა და გასაგები ახსნა.',
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('92cff6e9-719a-5b4b-8067-fe41a3a49805', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 5, 'ძალიან პრაქტიკული კურსი, რეალურ სამუშაოში მაშინვე გამომადგა.',
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('b33b0323-29db-58b9-ad0a-50ae9f88ac07', 'a207f124-6cfd-5603-9b56-d5ec17e0375b', 'a473b533-47d2-5ee6-8b76-cfbf1993d965', 4, 'კარგად სტრუქტურირებული მასალა და გასაგები ახსნა.',
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('70e75148-68f9-588f-96c3-6a0b8dad3ad4', '46725cbb-8270-5785-af01-3dc81d84f16a', 'de02c4ec-0b21-526d-ad74-6dc4d7b0dd4f', 5, 'ძალიან პრაქტიკული კურსი, რეალურ სამუშაოში მაშინვე გამომადგა.',
   now() - interval '10 days')
ON CONFLICT (id) DO NOTHING;
INSERT INTO course_review (id, course_id, user_id, rating, comment, created_at) VALUES
  ('68556bcc-ee80-561c-9736-de630fc26ca4', '46725cbb-8270-5785-af01-3dc81d84f16a', 'a473b533-47d2-5ee6-8b76-cfbf1993d965', 4, 'კარგად სტრუქტურირებული მასალა და გასაგები ახსნა.',
   now() - interval '11 days')
ON CONFLICT (id) DO NOTHING;

COMMIT;
