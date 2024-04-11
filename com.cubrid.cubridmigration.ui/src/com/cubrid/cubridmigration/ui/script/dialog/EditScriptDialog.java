/*
 * Copyright (C) 2009 Search Solution Corporation
 * Copyright (C) 2016 CUBRID Corporation
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.ui.script.dialog;

import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.script.MigrationScript;
import com.cubrid.cubridmigration.ui.script.MigrationScriptManager;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Dialog to rename migration script
 *
 * @author caoyilin
 * @version 1.0 - 2012-6-28 created by caoyilin
 */
public class EditScriptDialog extends Dialog {

    private StyledText txtName = null;
    private final MigrationScript script;
    private String name = "";

    private EditScriptDialog(Shell parentShell, MigrationScript script) {
        super(parentShell);
        this.script = script;
        name = script == null ? "" : script.getName();
        if (name == null) {
            name = "";
        }
    }

    /**
     * Edit script name
     *
     * @param parentShell shell
     * @param script MigrationScript
     */
    public static void getMigrationScriptName(Shell parentShell, MigrationScript script) {
        if (script == null) {
            return;
        }
        EditScriptDialog dialog = new EditScriptDialog(parentShell, script);
        if (dialog.open() == IDialogConstants.OK_ID) {
            script.setName(dialog.name);
            MigrationScriptManager.getInstance().save();
        }
    }

    /**
     * Return a new script name;
     *
     * @param parentShell Shell
     * @param defaultName String
     * @return script name
     */
    public static String getMigrationScriptName(Shell parentShell, String defaultName) {
        EditScriptDialog dialog = new EditScriptDialog(parentShell, null);

        dialog.name = defaultName == null ? "" : defaultName;
        if (dialog.open() != IDialogConstants.OK_ID) {
            return null;
        }
        return dialog.name;
    }

    /**
     * Don't support help content.
     *
     * @return false
     */
    public boolean isHelpAvailable() {
        return false;
    }

    /**
     * create Dialog Area
     *
     * @param parent Composite
     * @return Control
     */
    protected Control createDialogArea(Composite parent) {
        Composite parentComp = (Composite) super.createDialogArea(parent);
        final Composite composite = new Composite(parentComp, SWT.NONE);
        composite.setLayout(new GridLayout());
        composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createdbNameGroup(composite);
        return parentComp;
    }

    /**
     * Create Database Name Group
     *
     * @param composite Composite
     */
    private void createdbNameGroup(Composite composite) {
        final Composite dbnameGroup = new Composite(composite, SWT.BORDER);
        dbnameGroup.setLayout(new GridLayout(2, false));
        dbnameGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Label label = new Label(dbnameGroup, SWT.NONE);
        label.setText(Messages.lblScriptName);
        txtName = new StyledText(dbnameGroup, SWT.BORDER | SWT.SINGLE);
        txtName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        txtName.setTextLimit(100);
        txtName.setText(name);
        txtName.selectAll();
    }

    /** constrainShellSize */
    protected void constrainShellSize() {
        super.constrainShellSize();
        getShell().setSize(450, 162);
        getShell().setText(Messages.titleEditScript);
        CompositeUtils.centerDialog(getShell());
    }

    /**
     * createButtonsForButtonBar
     *
     * @param parent Composite
     */
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, Messages.btnOK, true);
        getButton(IDialogConstants.OK_ID).setEnabled(true);
        createButton(parent, IDialogConstants.CANCEL_ID, Messages.btnCancel, false);
    }

    /**
     * buttonPressed
     *
     * @param buttonId int
     */
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) {
            final String text = txtName == null ? "" : txtName.getText().trim();

            String error = MigrationScriptManager.getInstance().checkScriptName(text, script);
            if (StringUtils.isNotBlank(error)) {
                MessageDialog.openError(getShell(), Messages.msgError, error);
                return;
            }
            name = text;
        }
        super.buttonPressed(buttonId);
    }

    /** Return a value as boolean */
    protected boolean isResizable() {
        return true;
    }
}
