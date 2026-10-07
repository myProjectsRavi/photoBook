from __future__ import annotations

import unittest

from tools.benchmark.verify_blueprint_completion import STORY_IDS, validate


def backlog(status32: str = "IN_PROGRESS") -> str:
    rows = []
    for story_id in STORY_IDS:
        status = status32 if story_id == "S32" else "ACCEPTED"
        rows.append(f"| {story_id} | Epic | Feature | deps | {status} | scope |")
    return "\n".join(rows)


STATE = """authorized_branch: autopilot/epics-features-user-stories
current_story: S32 Final verification
status: IN_PROGRESS
"""


class BlueprintCompletionTest(unittest.TestCase):
    def test_valid_final_structure_passes(self):
        self.assertEqual([], validate(backlog(), STATE))

    def test_accepted_s32_also_passes(self):
        self.assertEqual([], validate(backlog("ACCEPTED"), STATE.replace("IN_PROGRESS", "ACCEPTED")))

    def test_incomplete_prior_story_fails(self):
        text = backlog().replace("| S20 | Epic | Feature | deps | ACCEPTED |", "| S20 | Epic | Feature | deps | IN_PROGRESS |")
        errors = validate(text, STATE)
        self.assertTrue(any("S20 must be ACCEPTED" in error for error in errors))

    def test_wrong_branch_fails(self):
        errors = validate(backlog(), STATE.replace("autopilot/epics-features-user-stories", "main"))
        self.assertTrue(any("authorized_branch mismatch" in error for error in errors))

    def test_missing_or_duplicate_story_fails(self):
        text = backlog().replace("| S10 | Epic | Feature | deps | ACCEPTED | scope |\n", "")
        self.assertTrue(validate(text, STATE))


if __name__ == "__main__":
    unittest.main()
