"""Generates docs/pms_schema.dbml (paste into https://dbdiagram.io/d).

Run from anywhere:  python PMS_Java/docs/generator/gen_dbml.py
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "gen_schema_doc.py")
OUT = os.path.join(HERE, "..", "pms_schema.dbml")

# Reuse the entity parser from the markdown generator (everything before the document is written).
code = open(SRC, encoding="utf-8").read()
code = code[: code.index("tables_sorted = []")]
ns = {"__file__": SRC}
exec(compile(code, "gen_schema_doc", "exec"), ns)
entities = ns["entities"]
GROUPS = ns["GROUPS"]
render_columns = ns["render_columns"]
enum_values = ns["enum_values"]


def snake(name):
    return re.sub(r"(?<!^)([A-Z])", r"_\1", name).lower()


def q(text):
    return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'"


# enum-typed columns: column name -> java enum class
def enum_columns(info):
    out = {}
    for jtype, fname, annos, doc in info["fields"]:
        if "@Enumerated" in annos:
            c = re.search(r"@Column\s*\(([^)]*)\)", annos)
            if not c:
                continue
            nm = re.search(r'name\s*=\s*"([^"]+)"', c.group(1))
            col = nm.group(1) if nm else snake(fname)
            out[col] = jtype
    return out


used_enums = {}
for t, info in entities.items():
    for col, jtype in enum_columns(info).items():
        vals = enum_values(jtype)
        if vals:
            used_enums[snake(jtype)] = vals

lines = []
lines.append("// PMS database structure (schema `pms`, PostgreSQL).")
lines.append("// Generated from the JPA entities in pms-db/src/main/java/com/sentrifugo/db/entity.")
lines.append("// Paste into https://dbdiagram.io/d to see the diagram.")
lines.append("")
lines.append("Project pms {")
lines.append("  database_type: 'PostgreSQL'")
lines.append("  Note: 'Performance Management System. Ids are UUIDs. Organisation, user, plant, department and "
             "designation ids are IAM ids kept as plain strings (no foreign key).'")
lines.append("}")
lines.append("")

for ename in sorted(used_enums):
    lines.append(f"Enum pms.{ename} {{")
    for v in used_enums[ename]:
        lines.append(f"  {v}")
    lines.append("}")
    lines.append("")

COMMON = [
    ("id", "uuid", ["pk", "not null"], "Primary key, assigned by the application"),
    ("created_by", "varchar(255)", [], "IAM user id that created the row"),
    ("modified_by", "varchar(255)", [], "IAM user id that last changed the row"),
    ("created_date", "timestamp", [], None),
    ("modified_date", "timestamp", [], None),
    ("is_active", "boolean", ["not null", "default: true"], "Soft-delete flag"),
]

order = []
for g, names in GROUPS:
    for n in names:
        if n in entities:
            order.append(n)
order += [n for n in sorted(entities) if n not in order]

refs = []
for t in order:
    info = entities[t]
    cols, fks = render_columns(info)
    enums = enum_columns(info)
    fk_cols = {c for c, _ in fks}
    lines.append(f"Table pms.{t} {{")
    for name, typ, attrs, note in COMMON:
        a = list(attrs)
        if note:
            a.append(f"note: {q(note)}")
        lines.append(f"  {name} {typ}" + (f" [{', '.join(a)}]" if a else ""))
    for col, dtype, nullable, note in cols:
        attrs = []
        if nullable == "NO":
            attrs.append("not null")
        typ = dtype
        if col in enums:
            en = snake(enums[col])
            if en in used_enums:
                typ = f"pms.{en}"
        # drop the generated "Values:" / "FK to" text from the note: enums and Refs carry that
        clean = re.sub(r"\s*Values:.*$", "", note or "")
        clean = re.sub(r"\s*FK to `[^`]+`", "", clean).strip()
        if clean:
            attrs.append(f"note: {q(clean)}")
        lines.append(f"  {col} {typ}" + (f" [{', '.join(attrs)}]" if attrs else ""))
    idx = []
    for n, cs in info["uniques"]:
        idx.append(f"    ({', '.join(cs)}) [unique, name: {q(n)}]" if len(cs) > 1
                   else f"    {cs[0]} [unique, name: {q(n)}]")
    for n, c in info["indexes"]:
        cs = [x.strip() for x in c.split(",")]
        idx.append(f"    ({', '.join(cs)}) [name: {q(n)}]" if len(cs) > 1 else f"    {cs[0]} [name: {q(n)}]")
    if idx:
        lines.append("")
        lines.append("  indexes {")
        lines.extend(idx)
        lines.append("  }")
    if info["desc"]:
        lines.append("")
        lines.append(f"  Note: {q(info['desc'])}")
    lines.append("}")
    lines.append("")
    for col, target in fks:
        refs.append(f"Ref: pms.{t}.{col} > pms.{target}.id")

lines.append("// Foreign keys")
lines.extend(sorted(refs))
lines.append("")
lines.append("// References that are not database foreign keys (kept consistent by the service layer)")
soft = [
    ("pms_cycle", "rating_scale_id", "rating_scale"),
    ("pms_cycle", "approval_workflow_id", "approval_workflow"),
    ("goal_assignment", "template_id", "goal_template"),
    ("goal_assignment", "change_kpi_id", "kpi_master"),
    ("goal_sheet_history", "kpi_id", "kpi_master"),
    ("user_notification", "cycle_id", "pms_cycle"),
]
for t, c, target in soft:
    if t in entities and target in entities:
        lines.append(f"// Ref: pms.{t}.{c} > pms.{target}.id")
lines.append("// appraisal_line.ref_id -> kpi_master.id (line_type KRA) or competency_master.id (competency lines)")
lines.append("")

for g, names in GROUPS:
    present = [n for n in names if n in entities]
    if present:
        gid = re.sub(r"[^a-z0-9]+", "_", g.lower()).strip("_")
        lines.append(f"TableGroup {gid} {{")
        for n in present:
            lines.append(f"  pms.{n}")
        lines.append("}")
        lines.append("")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("tables:", len(order), "enums:", len(used_enums), "refs:", len(refs))
