"""Generates docs/DATABASE_STRUCTURE.md from the JPA entities in pms-db.

Run from anywhere:  python PMS_Java/docs/generator/gen_schema_doc.py
(gen_dbml.py reuses this file's parser and writes docs/pms_schema.dbml.)
"""
import glob
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DB = os.path.join(ROOT, "pms-db", "src", "main", "java", "com", "sentrifugo", "db")
OUT = os.path.join(ROOT, "docs", "DATABASE_STRUCTURE.md")

JAVA_TYPES = {
    "UUID": "uuid", "Integer": "integer", "int": "integer", "Long": "bigint", "long": "bigint",
    "Boolean": "boolean", "boolean": "boolean", "LocalDate": "date", "LocalDateTime": "timestamp",
    "Double": "double precision", "Instant": "timestamp with time zone",
}

GROUPS = [
    ("Appraisal cycle", ["pms_cycle", "pms_cycle_stage", "pms_cycle_applicability", "pms_cycle_plant",
                         "pms_cycle_department", "pms_cycle_employment_type", "pms_cycle_eligibility_run",
                         "pms_cycle_notification"]),
    ("Masters and rating scales", ["kra_master", "kpi_master", "competency_master", "standard_rating_level",
                                   "rating_scale", "rating_scale_level"]),
    ("Goal templates", ["goal_template", "goal_template_kra", "goal_template_kpi", "goal_template_competency"]),
    ("Goal setting (manager, employee, HOD)", ["goal_assignment", "goal_assignment_target",
                                               "goal_change_request", "goal_sheet_history", "goal_transfer",
                                               "goal_transfer_kra"]),
    ("Progress, mid-year review and target revision", ["goal_progress", "mid_year_review", "target_revision",
                                                       "target_revision_row"]),
    ("Appraisal, calibration and workflow", ["appraisal", "appraisal_line", "calibration_distribution",
                                             "approval_workflow"]),
    ("Notifications", ["user_notification"]),
]


def balanced(text, start):
    """Text of the (...) group that opens at text[start] == '('."""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i]
    return ""


def enum_values(name):
    p = os.path.join(DB, "enums", name + ".java")
    if not os.path.exists(p):
        return []
    s = open(p, encoding="utf-8").read()
    body = s[s.index("{") + 1:]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//.*", "", body)
    head = body.split(";")[0]
    return [m.group(1) for m in re.finditer(r"^\s*([A-Z][A-Z0-9_]*)\s*(?:\(|,|$)", head, flags=re.M)]


def clean_doc(text):
    text = re.sub(r"\{@code ([^}]*)\}", r"`\1`", text)
    text = re.sub(r"\{@link ([^}]*)\}", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s*\*\s*", " ", text)
    return re.sub(r"\s+", " ", text).strip().strip("/").strip()


entities = {}
class_to_table = {}

for path in sorted(glob.glob(os.path.join(DB, "entity", "*Entity.java"))):
    s = open(path, encoding="utf-8").read()
    cls = os.path.basename(path)[:-5]
    m = re.search(r"@Table\s*\(", s)
    if not m:
        continue
    table_args = balanced(s, m.end() - 1)
    table = re.search(r'name\s*=\s*"([^"]+)"', table_args).group(1)
    schema = re.search(r'schema\s*=\s*"([^"]+)"', table_args)
    schema = schema.group(1) if schema else "public"
    uniques = []
    for u in re.finditer(r"@UniqueConstraint\s*\(", table_args):
        a = balanced(table_args, u.end() - 1)
        un = re.search(r'name\s*=\s*"([^"]+)"', a)
        cols = re.search(r"columnNames\s*=\s*(\{[^}]*\}|\"[^\"]+\")", a)
        cl = re.findall(r'"([^"]+)"', cols.group(1)) if cols else []
        uniques.append((un.group(1) if un else "", cl))
    indexes = []
    for u in re.finditer(r"@Index\s*\(", table_args):
        a = balanced(table_args, u.end() - 1)
        n = re.search(r'name\s*=\s*"([^"]+)"', a)
        c = re.search(r'columnList\s*=\s*"([^"]+)"', a)
        indexes.append((n.group(1) if n else "", c.group(1) if c else ""))

    # class javadoc: the last /** */ before the class declaration
    cm = re.search(r"public\s+class\s+" + cls, s)
    head = s[:cm.start()]
    docs = re.findall(r"/\*\*(.*?)\*/", head, flags=re.S)
    desc = clean_doc(docs[-1]) if docs else ""

    body = s[cm.end():]
    fields = []
    doc = ""
    annos = ""
    for line in body.splitlines():
        t = line.strip()
        if t.startswith("/**"):
            doc = t
            if "*/" not in t:
                doc = t
            continue
        if doc and not doc.endswith("*/") and (t.startswith("*") or t.endswith("*/")):
            doc += " " + t
            continue
        if t.startswith("@"):
            annos += " " + t
            continue
        fm = re.match(r"private\s+(?:final\s+)?([\w.<>]+)\s+(\w+)\s*(?:=.*)?;", t)
        if fm:
            fields.append((fm.group(1), fm.group(2), annos, clean_doc(doc)))
            annos = ""
            doc = ""
        elif t and not t.startswith("*"):
            annos = ""
            doc = ""
    entities[table] = dict(cls=cls, schema=schema, desc=desc, uniques=uniques, indexes=indexes, fields=fields,
                           extends=re.search(r"extends\s+(\w+)", s[cm.start():cm.start() + 200]))
    class_to_table[cls] = table

BASE_COLUMNS = [
    ("id", "uuid", "NO", "Primary key, assigned by the application"),
    ("created_by", "varchar(255)", "YES", "IAM user id that created the row"),
    ("modified_by", "varchar(255)", "YES", "IAM user id that last changed the row"),
    ("created_date", "timestamp", "YES", "Set when the row is created"),
    ("modified_date", "timestamp", "YES", "Set when the row is changed"),
    ("is_active", "boolean", "NO", "Soft-delete flag, default true"),
]


def render_columns(info):
    rows = []
    fks = []
    for jtype, fname, annos, doc in info["fields"]:
        col = None
        nullable = "YES"
        dtype = None
        note = doc
        jc = re.search(r"@JoinColumn\s*\(([^)]*)\)", annos)
        c = re.search(r"@Column\s*\(([^)]*)\)", annos)
        if jc:
            col = re.search(r'name\s*=\s*"([^"]+)"', jc.group(1)).group(1)
            if "nullable = false" in jc.group(1) or "optional = false" in annos:
                nullable = "NO"
            dtype = "uuid"
            target = class_to_table.get(jtype, jtype)
            fks.append((col, target))
            note = (note + " " if note else "") + f"FK to `{target}`"
        elif c:
            args = c.group(1)
            nm = re.search(r'name\s*=\s*"([^"]+)"', args)
            col = nm.group(1) if nm else re.sub(r"([A-Z])", lambda m: "_" + m.group(1).lower(), fname)
            if "nullable = false" in args:
                nullable = "NO"
            ln = re.search(r"length\s*=\s*(\d+)", args)
            pr = re.search(r"precision\s*=\s*(\d+)", args)
            sc = re.search(r"scale\s*=\s*(\d+)", args)
            cd = re.search(r'columnDefinition\s*=\s*"([^"]+)"', args)
            if cd and cd.group(1).upper().startswith("TEXT"):
                dtype = "text"
            elif "@Enumerated" in annos:
                vals = enum_values(jtype)
                dtype = f"varchar({ln.group(1) if ln else 255})"
                if vals:
                    note = (note + " " if note else "") + "Values: " + ", ".join(f"`{v}`" for v in vals)
            elif jtype == "BigDecimal" or jtype.endswith(".BigDecimal"):
                dtype = f"numeric({pr.group(1) if pr else 19},{sc.group(1) if sc else 2})"
            elif jtype == "String":
                dtype = f"varchar({ln.group(1) if ln else 255})"
            else:
                dtype = JAVA_TYPES.get(jtype.split('.')[-1], jtype.split('.')[-1])
            if "unique = true" in args:
                note = (note + " " if note else "") + "Unique"
            if cd and "default" in cd.group(1).lower():
                note = (note + " " if note else "") + f"Default: {cd.group(1)}"
        else:
            continue
        if "@Builder.Default" in annos and nullable == "NO" and jtype in ("Boolean", "Integer"):
            pass
        rows.append((col, dtype, nullable, note))
    return rows, fks


tables_sorted = []
for gname, names in GROUPS:
    for n in names:
        if n in entities:
            tables_sorted.append((gname, n))
known = {n for _, n in tables_sorted}
for n in sorted(entities):
    if n not in known:
        tables_sorted.append(("Other", n))

out = []
out.append("# PMS database structure\n")
out.append("Schema `pms` (PostgreSQL). This file is generated from the JPA entities in "
           "`pms-db/src/main/java/com/sentrifugo/db/entity` and is therefore the same structure Hibernate creates "
           "(`spring.jpa.hibernate.ddl-auto=update`). There is no Flyway or Liquibase: new columns and tables are "
           "added automatically on startup, existing ones are never dropped or rewritten.\n")
out.append("Written for: backend and database engineers working on PMS_Java.\n")
out.append(f"**{len(entities)} tables.** Every table also has the six common columns below (inherited from "
           "`BaseEntity`), which are not repeated per table.\n")
out.append("## Common columns (every table)\n")
out.append("| Column | Type | Null | Notes |\n|---|---|---|---|")
for col, typ, nul, note in BASE_COLUMNS:
    out.append(f"| `{col}` | {typ} | {nul} | {note} |")
out.append("")
out.append("Ids are UUIDs. Organisation, user, plant, department and designation ids are **IAM ids stored as plain "
           "strings** (no foreign key): the IAM data lives in another service. Every business table is scoped by "
           "`organisation_id` either directly or through its parent.\n")

out.append("## References that are not foreign keys\n")
out.append("These uuid columns point at another PMS table but carry no database constraint; the service layer "
           "keeps them consistent.\n")
out.append("| Column | Refers to |\n|---|---|")
for col, ref in [
    ("pms_cycle.rating_scale_id", "`rating_scale.id`"),
    ("pms_cycle.approval_workflow_id", "`approval_workflow.id`"),
    ("goal_assignment.template_id", "`goal_template.id` (the template the sheet was set against)"),
    ("goal_assignment.change_kpi_id", "`kpi_master.id` (the KPI of the employee's open change request)"),
    ("goal_sheet_history.kpi_id", "`kpi_master.id` (null for sheet-level steps)"),
    ("appraisal_line.ref_id", "`kpi_master.id` for KRA lines, `competency_master.id` for competency lines "
                              "(see `line_type`)"),
    ("user_notification.cycle_id", "`pms_cycle.id`"),
]:
    out.append(f"| `{col}` | {ref} |")
out.append("")
out.append("## Tables by area\n")
out.append("| Area | Tables |\n|---|---|")
for gname, names in GROUPS:
    present = [n for n in names if n in entities]
    if present:
        out.append(f"| {gname} | " + ", ".join(f"`{n}`" for n in present) + " |")
others = [n for g, n in tables_sorted if g == "Other"]
if others:
    out.append("| Other | " + ", ".join(f"`{n}`" for n in others) + " |")
out.append("")

# relationships
out.append("## Relationships (foreign keys)\n")
out.append("```mermaid\nerDiagram")
edges = []
for t, info in entities.items():
    _, fks = render_columns(info)
    for col, target in fks:
        edges.append((target, t, col))
for target, t, col in sorted(edges):
    out.append(f"    {target} ||--o{{ {t} : \"{col}\"")
out.append("```\n")

last_group = None
for gname, t in tables_sorted:
    info = entities[t]
    if gname != last_group:
        out.append(f"\n## {gname}\n")
        last_group = gname
    out.append(f"### `{info['schema']}.{t}`\n")
    if info["desc"]:
        out.append(info["desc"] + "\n")
    cols, fks = render_columns(info)
    out.append("| Column | Type | Null | Notes |\n|---|---|---|---|")
    for col, typ, nul, note in cols:
        out.append(f"| `{col}` | {typ} | {nul} | {note} |")
    if info["uniques"]:
        out.append("\n**Unique:** " + "; ".join(
            f"`{', '.join(cols_)}`" + (f" ({n})" if n else "") for n, cols_ in info["uniques"]))
    if info["indexes"]:
        out.append("\n**Indexes:** " + "; ".join(
            f"`{c}`" + (f" ({n})" if n else "") for n, c in info["indexes"]))
    out.append("")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
print("tables:", len(entities))
for g, t in tables_sorted:
    c, f = render_columns(entities[t])
    print(f"{t}: {len(c)} cols, {len(f)} fks")
