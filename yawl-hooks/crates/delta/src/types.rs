use serde::{Deserialize, Serialize};

/// A typed fact transition — the fundamental unit of the delta engine.
/// Never line numbers. Never unified patches. Only semantic transitions.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum Delta {
    /// A declaration was added, removed, or modified in a source file.
    Declaration {
        decl_kind: DeclKind,
        name: String,
        change: Change,
    },
    /// A rule or predicate in CLAUDE.md / SPR changed.
    Rule {
        section: String,
        predicate: String,
        change: Change,
    },
    /// An acceptance criterion on a ticket changed.
    Criterion {
        ticket_id: String,
        ac: String,
        change: Change,
    },
    /// A dependency version constraint changed.
    Dependency {
        name: String,
        from: String,
        to: String,
        breaking: bool,
    },
    /// A behavioral description changed (changelog / spec entry).
    Behavior {
        component: String,
        old: String,
        new: String,
        breaking: bool,
    },
    /// RDF quad additions and removals.
    Quad {
        adds: Vec<RdfQuad>,
        removes: Vec<RdfQuad>,
    },
}

/// The kind of declaration that changed.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum DeclKind {
    Function,
    Type,
    Constant,
    Import,
    Module,
    Field,
    Method,
    Enum,
    Trait,
    Interface,
    Class,
    Annotation,
}

/// How a fact changed.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub enum Change {
    Added,
    Removed,
    Modified { from: String, to: String },
}

/// An RDF quad (subject, predicate, object, graph).
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq, PartialOrd, Ord)]
pub struct RdfQuad {
    pub subject: String,
    pub predicate: String,
    pub object: String,
    pub graph: Option<String>,
}

/// Determines which semantic unit an artifact uses for diffing.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SemanticUnit {
    /// Java / Rust / Python source declarations
    Declaration,
    /// CLAUDE.md or SPR rule predicates
    Rule,
    /// JIRA ticket fields (acceptance criteria)
    Criterion,
    /// Cargo.toml / pom.xml version constraints
    DependencyConstraint,
    /// Changelog or spec behavioral change entries
    Behavior,
    /// RDF / N-Quad tuples
    Quad,
}

impl SemanticUnit {
    /// Infer semantic unit from file extension or path suffix.
    pub fn from_path(path: &str) -> Self {
        if path.ends_with(".toml") && (path.contains("Cargo") || path.contains("pom")) {
            return SemanticUnit::DependencyConstraint;
        }
        if path.ends_with("CLAUDE.md") || path.ends_with("SPR.md") {
            return SemanticUnit::Rule;
        }
        if path.contains("jira/tickets/") && path.ends_with(".toml") {
            return SemanticUnit::Criterion;
        }
        if path.ends_with(".nq") || path.ends_with(".ttl") || path.ends_with(".n3") {
            return SemanticUnit::Quad;
        }
        if path.ends_with("CHANGELOG.md") || path.ends_with("CHANGES.md") {
            return SemanticUnit::Behavior;
        }
        if path.ends_with(".java") || path.ends_with(".rs") || path.ends_with(".py") {
            return SemanticUnit::Declaration;
        }
        SemanticUnit::Declaration
    }
}
