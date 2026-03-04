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

//! Basic usage example for the YAWL Process Mining Bridge
//!
//! This example demonstrates how to use functions directly from the process_mining crate.

use process_mining::*;

fn main() {
    println!("YAWL Process Mining Bridge - Basic Usage Example");

    // Create a simple EventLog
    let log = EventLog::new();
    println!("Created EventLog with {} traces", log.traces.len());

    // OCEL creation would require actual data
    println!("OCEL functionality requires imported data");

    println!("Example completed successfully!");
}