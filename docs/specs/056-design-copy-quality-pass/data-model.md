# Data Model: Visual Design & Copy Quality Pass

No data model changes — this feature is a frontend-only presentation/copy correction. It introduces no new entity, field, table, migration, or API contract, and does not change any existing one.

## Frontend shapes touched (unchanged in shape, only in usage)

- `SidebarNavItem` (`frontend/src/components/Sidebar.tsx`) — existing type, not modified. `Sidebar`'s own root element's width/positioning classes may move or change (Decision 1, research.md) but its props contract (`items`, `activeRole`) is unchanged.
- No new prop, no new component state, no new context is introduced by either the layout restructuring or the `HomePage.tsx` copy rewrite — the copy change is a literal string replacement in existing JSX, not a new data-driven content model.
