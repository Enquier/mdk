package gov.nasa.jpl.mbee.mdk.mms.actions;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nomagic.magicdraw.annotation.Annotation;
import com.nomagic.magicdraw.annotation.AnnotationAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import gov.nasa.jpl.mbee.mdk.api.incubating.MDKConstants;
import gov.nasa.jpl.mbee.mdk.api.incubating.convert.Converters;
import gov.nasa.jpl.mbee.mdk.http.ServerException;
import gov.nasa.jpl.mbee.mdk.json.JacksonUtils;
import gov.nasa.jpl.mbee.mdk.mms.MMSArtifact;
import gov.nasa.jpl.mbee.mdk.mms.MMSUtils;
import gov.nasa.jpl.mbee.mdk.util.TaskRunner;
import gov.nasa.jpl.mbee.mdk.validation.IRuleViolationAction;
import gov.nasa.jpl.mbee.mdk.validation.RuleViolationAction;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import javax.annotation.CheckForNull;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class CommitDiagramArtifactsAction extends RuleViolationAction implements AnnotationAction, IRuleViolationAction {
    private final Diagram diagram;
    private final Set<String> initialArtifactIds;
    private final Set<MMSArtifact> artifacts;
    private final Project project;

    public CommitDiagramArtifactsAction(Diagram diagram, Set<String> initialArtifactIds, Set<MMSArtifact> artifacts, Project project) {
        super(CommitDiagramArtifactsAction.class.getSimpleName(), "Commit to MMS", null, null);
        this.diagram = diagram;
        this.initialArtifactIds = initialArtifactIds;
        this.artifacts = artifacts;
        this.project = project;
    }

    @Override
    public void execute(Collection<Annotation> annotations) {
        annotations.forEach(annotation -> annotation.getActions().stream().filter(action -> action instanceof CommitDiagramArtifactsAction).forEach(action -> action.actionPerformed(null)));
    }

    @Override
    public void actionPerformed(@CheckForNull ActionEvent actionEvent) {
        URIBuilder artifactsUriBuilder = MMSUtils.getServiceProjectsRefsArtifactsUri(project);
        if (artifactsUriBuilder == null) {
            return;
        }
        URI artifactsUri;
        try {
            artifactsUri = artifactsUriBuilder.build();
        } catch (URISyntaxException e) {
            Application.getInstance().getGUILog().log("[ERROR] Unable to commit artifact " + Converters.getElementToHumanNameConverter().apply(diagram) + ". Reason: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        TaskRunner.runWithProgressStatus(progressStatus -> {
            try {
                URIBuilder elementsRequestUri = MMSUtils.getServiceProjectsRefsElementsUri(project);
                if (elementsRequestUri == null) {
                    return;
                }
                for (MMSArtifact artifact : artifacts) {
                    HttpPost request = new HttpPost(artifactsUri);
                    HttpEntity entity = MultipartEntityBuilder.create().addTextBody(MDKConstants.ID_KEY, artifact.getId()).addTextBody(MDKConstants.CHECKSUM_KEY, artifact.getChecksum()).addTextBody("source", "magicdraw").addBinaryBody("file", artifact.getInputStream(), artifact.getContentType(), artifact.getId() + ".tmp").build();
                    request.setEntity(entity);
                    MMSUtils.sendMMSRequest(project, request, progressStatus);
                }
                ObjectNode objectNode = JacksonUtils.getObjectMapper().createObjectNode();
                objectNode.put(MDKConstants.ID_KEY, Converters.getElementToIdConverter().apply(diagram));
                ArrayNode updatedArtifactIdsNode = objectNode.putArray(MDKConstants.ARTIFACT_IDS_KEY);
                initialArtifactIds.forEach(updatedArtifactIdsNode::add);
                artifacts.stream().map(MMSArtifact::getId).forEachOrdered(updatedArtifactIdsNode::add);
                File file = MMSUtils.createEntityFile(CommitClientElementAction.class, ContentType.APPLICATION_JSON, Collections.singleton(objectNode), MMSUtils.JsonBlobType.ELEMENT_JSON);
                HttpRequestBase request = MMSUtils.buildRequest(MMSUtils.HttpRequestType.POST, elementsRequestUri, file, ContentType.APPLICATION_JSON);
                MMSUtils.sendMMSRequest(project, request, progressStatus);
            } catch (IOException | ServerException | URISyntaxException | GeneralSecurityException e) {
                e.printStackTrace();
                Application.getInstance().getGUILog().log("[ERROR] Failed to commit diagram artifacts for " + Converters.getElementToHumanNameConverter().apply(diagram) + ". Reason: " + e.getMessage());
            }
        }, "Artifacts Commit", true, TaskRunner.ThreadExecutionStrategy.SINGLE);
    }

    @Override
    public boolean canExecute(Collection<Annotation> collection) {
        return true;
    }

    /*
        ObjectNode imageEntry = JacksonUtils.getObjectMapper().createObjectNode();
        //for (Element e: Project.getProject(image.getImage()).getDiagram(image.getImage()).getUsedModelElements(false)) {
        //    addToElements(e);
        //}
        // export image - also keep track of exported images
        DiagramPresentationElement diagram = Application.getInstance().getProject().getDiagram(image.getImage());
        String svgFilename = Converters.getElementToIdConverter().apply(image.getImage());

        // create image file
        String userhome = System.getProperty("user.home");
        File directory;
        if (userhome != null) {
            directory = new File(userhome + File.separator + "mdkimages");
        }
        else {
            directory = new File("mdkimages");
        }
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // export the image file
        File svgDiagramFile = new File(directory, svgFilename);
        boolean initialUseSVGTestTag = Application.getInstance().getEnvironmentOptions().getGeneralOptions().isUseSVGTextTag();
        Application.getInstance().getEnvironmentOptions().getGeneralOptions().setUseSVGTextTag(true);
        try {
            ImageExporter.export(diagram, ImageExporter.SVG, svgDiagramFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Application.getInstance().getEnvironmentOptions().getGeneralOptions().setUseSVGTextTag(initialUseSVGTestTag);
        }

        // calculate the checksum
        long cs = 0;
        try {
            RandomAccessFile f = new RandomAccessFile(svgDiagramFile.getAbsolutePath(), "r");
            byte[] data = new byte[(int) f.length()];
            f.read(data);
            f.close();
            Checksum checksum = new CRC32();
            checksum.update(data, 0, data.length);
            cs = checksum.getValue();
        } catch (IOException e) {
            gl.log("Could not calculate checksum: " + e.getMessage());
            e.printStackTrace();
        }

        // Lets rename the file to have the hash code
        // make sure this matches what's in the View Editor ImageResource.java
        String FILE_EXTENSION = "svg";
        //gl.log("Exporting diagram to: " + svgDiagramFile.getAbsolutePath());

        // keep record of all images found
        imageEntry.put("cs", String.valueOf(cs));
        imageEntry.put("abspath", svgDiagramFile.getAbsolutePath());
        imageEntry.put("extension", FILE_EXTENSION);
        images.put(svgFilename, imageEntry);

        //MDEV #674 -- Update the type and id: was hard coded.
        //
     */
}
