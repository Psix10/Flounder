ALTER TABLE organization_members
    ADD COLUMN assigned_by_user_id uuid REFERENCES users(id);
COMMENT ON COLUMN organization_members.assigned_by_user_id IS
    'Actor of initial API assignment; NULL for legacy memberships with unknown actor. Replays do not rewrite it.';
