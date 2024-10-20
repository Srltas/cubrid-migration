/*
 * Copyright (C) 2008 Search Solution Corporation.
 * Copyright (C) 2016 CUBRID Corporation.
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

package com.cubrid.cubridmigration.ui.common;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolTip;

public class TooltipLabel extends Label {
    private ToolTip toolTip = null;
    private Composite parent;

    public TooltipLabel(Composite parent, int style) {
        super(parent, style);
        this.parent = parent;

        this.addListener(
                SWT.MouseEnter,
                (event) -> {
                    if (toolTip != null) {
                        toolTip.setVisible(true);
                    }
                });
        this.addListener(
                SWT.MouseExit,
                (event) -> {
                    if (toolTip != null) {
                        toolTip.setVisible(false);
                    }
                });

        this.addDisposeListener(
                new DisposeListener() {
                    @Override
                    public void widgetDisposed(DisposeEvent e) {
                        if (toolTip != null) {
                            toolTip.dispose();
                            toolTip = null;
                        }
                    }
                });
    }

    @Override
    public void setToolTipText(String text) {
        if (toolTip == null || toolTip.isDisposed()) {
            toolTip = new ToolTip(parent.getShell(), SWT.BALLOON | SWT.ICON_INFORMATION);
        }
        toolTip.setAutoHide(false);
        toolTip.setVisible(false);
        toolTip.setMessage(text);
    }

    @Override
    protected void checkSubclass() {}
}
