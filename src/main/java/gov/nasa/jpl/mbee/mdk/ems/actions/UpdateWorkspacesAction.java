package gov.nasa.jpl.mbee.mdk.ems.actions;

import gov.nasa.jpl.mbee.mdk.ems.ExportUtility;

import java.awt.event.ActionEvent;

public class UpdateWorkspacesAction extends MMSAction {
    private static final long serialVersionUID = 1L;
    public static final String DEFAULT_ID = "UpdateWorkspaces";

    public UpdateWorkspacesAction() {
        super(DEFAULT_ID, "(Update Workspaces Mappings)", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ExportUtility.updateWorkspaceIdMapping();
    }
}


