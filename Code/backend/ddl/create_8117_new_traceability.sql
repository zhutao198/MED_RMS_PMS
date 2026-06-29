-- Create traceability chains for new 8117 requirements

-- Chain 1: ECG Acquisition (62 -> 72 -> 80 -> 85)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (62, 72, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (62, 80, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (62, 85, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (72, 80, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (72, 85, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (80, 85, 1);

-- Chain 2: AI Diagnostic (63 -> 73 -> 81 -> 86)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (63, 73, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (63, 81, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (63, 86, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (73, 81, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (73, 86, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (81, 86, 1);

-- Chain 3: Network (64 -> 74 -> 82 -> 87)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (64, 74, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (64, 82, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (64, 87, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (74, 82, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (74, 87, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (82, 87, 1);

-- Chain 4: Critical Alert (65 -> 75 -> 83 -> 88)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (65, 75, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (65, 83, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (65, 88, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (75, 83, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (75, 88, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (83, 88, 1);

-- Chain 5: Platform (66 -> 76 -> 84 -> 89)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (66, 76, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (66, 84, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (66, 89, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (76, 84, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (76, 89, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (84, 89, 1);