# Context

_Data flow and co-location context. Use to understand which files work together._

## Critical Flows

_Most central modules by connectivity. Changes here propagate widely._

| Module | Callers | Dependencies |
|--------|---------|--------------|
| `backend/app/database.py` | 1 | 0 |
| `backend/app/main.py` | 0 | 2 |
| `backend/app/models.py` | 1 | 0 |

---

## Quick Reference

**"What files work together for feature X?"**
→ Check Module Clusters above.

**"Where does data flow from this endpoint?"**
→ Check Critical Flows and Dependency Chains.

**"Where are external connections?"**
→ Check External Integrations.


_Generated: 2026-09-23T05:19:09.467Z_
