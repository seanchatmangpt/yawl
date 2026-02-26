use blake3::Hasher;

/// Compute a blake3 hash of arbitrary bytes, returned as a hex string.
pub fn blake3_hex(data: &[u8]) -> String {
    let hash = blake3::hash(data);
    hash.to_hex().to_string()
}

/// Compute a blake3 hash of a string, returned as a hex string.
pub fn blake3_str(s: &str) -> String {
    blake3_hex(s.as_bytes())
}

/// Compute a blake3 hash of JSON-serializable data (canonical JSON).
pub fn blake3_json<T: serde::Serialize>(value: &T) -> anyhow::Result<String> {
    let canonical = serde_json::to_string(value)?;
    Ok(blake3_str(&canonical))
}

/// A streaming blake3 hasher for large content.
pub struct StreamHasher(Hasher);

impl StreamHasher {
    pub fn new() -> Self {
        Self(Hasher::new())
    }

    pub fn update(&mut self, data: &[u8]) {
        self.0.update(data);
    }

    pub fn finalize_hex(self) -> String {
        self.0.finalize().to_hex().to_string()
    }
}

impl Default for StreamHasher {
    fn default() -> Self {
        Self::new()
    }
}
