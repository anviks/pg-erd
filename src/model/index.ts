/**
 * The diagram model: entities (tables/views) with attributes, and associations (foreign keys).
 */

export type EntityKind = 'table' | 'view';

export interface Attribute {
  name: string;
  /** Type label as PostgreSQL prints it, e.g. `character varying(255)`, `numeric(10,2)`, `integer[]`. */
  fullTypeName: string;
  /** Icon resource id for the type, e.g. `types/number`. */
  iconId: string;
  /** NOT NULL */
  required: boolean;
  /** Part of the entity's key (PK, else the first unique constraint, else a unique index on NOT NULL columns). */
  inPrimaryKey: boolean;
  /** Part of any FK of the entity (even one whose target is not in the diagram). */
  inForeignKey: boolean;
  /** Part of any PK/unique/FK constraint (the KEYS visibility mode). */
  inAnyConstraint: boolean;
  comment?: string | null;
}

export class Entity {
  readonly attributes: Attribute[] = [];
  /** The root table of a single-table diagram (drawn with the accent header colour). */
  primary = false;

  constructor(
    public readonly schema: string,
    public readonly name: string,
    public readonly kind: EntityKind,
    public readonly comment: string | null = null,
  ) {}

  get isView(): boolean {
    return this.kind === 'view';
  }
  get fullName(): string {
    return `${this.schema}.${this.name}`;
  }
  addAttribute(a: Attribute): void {
    this.attributes.push(a);
  }
  keyAttributes(): Attribute[] {
    return this.attributes.filter((a) => a.inPrimaryKey);
  }
  toString(): string {
    return this.fullName;
  }
}

/**
 * A foreign key drawn as a connection: `source` is the FK-owning (child) entity and `target` the
 * referenced (parent) entity.
 */
export interface Association {
  name: string;
  source: Entity;
  target: Entity;
  sourceColumns: string[];
  targetColumns: string[];
  /** All FK columns are part of the child's PK (or a unique constraint if it has no PK); never for self-references. */
  identifying: boolean;
  /** Any FK column is nullable. */
  optional: boolean;
  /** A unique index on the child covers exactly the FK columns (crow's foot "1" vs "1..n"). */
  oneToOne: boolean;
  comment?: string | null;
}

export function isSelfReference(a: Association): boolean {
  return a.source === a.target;
}

export class Diagram {
  readonly entities: Entity[] = [];
  readonly associations: Association[] = [];

  constructor(public readonly name: string) {}

  addEntity(e: Entity): void {
    this.entities.push(e);
  }
  addAssociation(a: Association): void {
    if (!this.entities.includes(a.source) || !this.entities.includes(a.target)) {
      throw new Error(`association ${a.name} references an entity outside the diagram`);
    }
    this.associations.push(a);
  }
  /** Associations where `e` is the source (FK child). */
  sourceAssociations(e: Entity): Association[] {
    return this.associations.filter((a) => a.source === e);
  }
  get isEmpty(): boolean {
    return this.entities.length === 0;
  }
}
