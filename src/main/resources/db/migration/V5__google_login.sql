-- Google sign-in for everyone; password sign-in only for flagged accounts (test accounts, break-glass admin).
ALTER TABLE user_account ADD COLUMN local_login BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_account SET local_login = TRUE
 WHERE lower(email) IN ('admin@psgitech.local',
   'teststudent@psgitech.ac.in','testtutor@psgitech.ac.in','testtutor2@psgitech.ac.in','testbackup@psgitech.ac.in',
   'testhod@psgitech.ac.in','testhead@psgitech.ac.in','testsecurity@psgitech.ac.in','testadmin@psgitech.ac.in');

-- people who sign in with Google have no password to change
UPDATE user_account SET must_change_password = FALSE WHERE local_login = FALSE;
