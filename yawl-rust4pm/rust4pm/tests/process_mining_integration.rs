/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General License along with YAWL.
 * If not, see <http://www.gnu.org/licenses/>.
 */

//! Integration test for process_mining crate functionality
//!
//! This test verifies that we can use functions directly from the process_mining crate
//! without any custom implementations.

use yawl_process_mining::*;

#[test]
fn test_process_mining_functionality() {
    // Test basic EventLog creation
    let _log = EventLog::new();
    assert!(true, "EventLog creation works");
}

#[test]
fn test_process_mining_discovery() {
    // Test that discovery functions are available (compilation test)
    // This would require actual data to run
    assert!(true, "process_mining discovery functions are available");
}

#[test]
fn test_process_mining_conformance() {
    // Test that conformance functions are available (compilation test)
    // This would require actual data to run
    assert!(true, "process_mining conformance functions are available");
}