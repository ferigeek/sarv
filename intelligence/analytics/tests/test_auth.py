import unittest

from analytics.auth import (
    LoginThrottle,
    check_credentials,
    create_token,
    verify_token,
)

SECRET = "test-secret"
OTHER_SECRET = "other-secret"


class TokenTest(unittest.TestCase):
    def test_round_trip(self):
        token = create_token("admin", SECRET, 3600, now=1000.0)
        self.assertEqual(verify_token(token, SECRET, now=2000.0), "admin")

    def test_expired_rejected(self):
        token = create_token("admin", SECRET, 100, now=1000.0)
        self.assertIsNone(verify_token(token, SECRET, now=1200.0))

    def test_wrong_secret_rejected(self):
        token = create_token("admin", SECRET, 3600, now=1000.0)
        self.assertIsNone(verify_token(token, OTHER_SECRET, now=2000.0))

    def test_tampered_payload_rejected(self):
        token = create_token("admin", SECRET, 3600, now=1000.0)
        head, sig = token.split(".")
        forged = head[:-2] + ("AA" if not head.endswith("AA") else "BB")
        self.assertIsNone(verify_token(f"{forged}.{sig}", SECRET, now=2000.0))

    def test_garbage_rejected(self):
        for bad in ["", "no-dot-at-all", "a.b.c", "!!!.???"]:
            self.assertIsNone(verify_token(bad, SECRET, now=1000.0))


class CredentialsTest(unittest.TestCase):
    def test_match(self):
        self.assertTrue(check_credentials("admin", "s3cret", "admin", "s3cret"))

    def test_mismatch(self):
        self.assertFalse(check_credentials("admin", "wrong", "admin", "s3cret"))
        self.assertFalse(check_credentials("root", "s3cret", "admin", "s3cret"))


class ThrottleTest(unittest.TestCase):
    def test_lockout_after_max_fails(self):
        throttle = LoginThrottle(max_fails=3, lockout_seconds=60)
        self.assertFalse(throttle.is_locked("1.2.3.4", now=0.0))
        throttle.register_fail("1.2.3.4", now=1.0)
        throttle.register_fail("1.2.3.4", now=2.0)
        self.assertFalse(throttle.is_locked("1.2.3.4", now=3.0))
        throttle.register_fail("1.2.3.4", now=4.0)
        self.assertTrue(throttle.is_locked("1.2.3.4", now=5.0))

    def test_lockout_expires(self):
        throttle = LoginThrottle(max_fails=2, lockout_seconds=60)
        throttle.register_fail("1.2.3.4", now=0.0)
        throttle.register_fail("1.2.3.4", now=1.0)
        self.assertTrue(throttle.is_locked("1.2.3.4", now=30.0))
        self.assertFalse(throttle.is_locked("1.2.3.4", now=61.0))

    def test_reset_clears(self):
        throttle = LoginThrottle(max_fails=1, lockout_seconds=60)
        throttle.register_fail("1.2.3.4", now=0.0)
        self.assertTrue(throttle.is_locked("1.2.3.4", now=1.0))
        throttle.reset("1.2.3.4")
        self.assertFalse(throttle.is_locked("1.2.3.4", now=1.0))

    def test_per_ip_isolation(self):
        throttle = LoginThrottle(max_fails=1, lockout_seconds=60)
        throttle.register_fail("1.2.3.4", now=0.0)
        self.assertFalse(throttle.is_locked("5.6.7.8", now=1.0))


if __name__ == "__main__":
    unittest.main()
