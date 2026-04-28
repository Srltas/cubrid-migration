ALTER TABLE [MAIN_USER].[e2e_order] DROP CONSTRAINT [fk_e2e_order_cust];
ALTER TABLE [MAIN_USER].[e2e_order_line] DROP CONSTRAINT [fk_e2e_order_line_ord];
ALTER TABLE [MAIN_USER].[e2e_employee] DROP CONSTRAINT [fk_e2e_employee_mgr];
