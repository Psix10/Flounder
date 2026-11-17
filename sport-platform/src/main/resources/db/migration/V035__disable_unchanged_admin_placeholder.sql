-- Do not alter the historical V002 checksum or disable an administrator
-- whose placeholder password has already been replaced.
UPDATE users
SET status = 'inactive', updated_at = now()
WHERE id = '11111111-1111-1111-1111-111111111111'::uuid
  AND email = 'admin@sport-platform.local'
  AND password_hash = '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH'
  AND lower(status) = 'active';
