-- Create traceability chain for 8117 project
-- URS -> PRS -> SRS -> DRS

-- Chain 1: ECG Acquisition (42 -> 47 -> 52 -> 57)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (42, 47, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (42, 52, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (42, 57, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (47, 52, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (47, 57, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (52, 57, 1);

-- Chain 2: AI Diagnostic (43 -> 48 -> 53 -> 58)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (43, 48, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (43, 53, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (43, 58, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (48, 53, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (48, 58, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (53, 58, 1);

-- Chain 3: Network (44 -> 49 -> 54 -> 59)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (44, 49, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (44, 54, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (44, 59, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (49, 54, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (49, 59, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (54, 59, 1);

-- Chain 4: Critical Alert (45 -> 50 -> 55 -> 60)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (45, 50, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (45, 55, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (45, 60, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (50, 55, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (50, 60, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (55, 60, 1);

-- Chain 5: Platform (46 -> 51 -> 56 -> 61)
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (46, 51, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (46, 56, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (46, 61, 3);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (51, 56, 1);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (51, 61, 2);
INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES (56, 61, 1);