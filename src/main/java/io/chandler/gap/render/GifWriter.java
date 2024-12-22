package io.chandler.gap.render;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * GifWriter is responsible for creating animated GIFs from a list of BufferedImage frames.
 */
public class GifWriter {

	/**
	 * Creates an animated GIF from the provided frames and saves it to the specified file.
	 *
	 * @param frames List of BufferedImage frames to include in the GIF.
	 * @param output The output file where the GIF will be saved.
	 * @throws Exception If an error occurs during writing.
	 */
	public void createAnimatedGif(List<BufferedImage> frames, File output) throws Exception {
		if (frames == null || frames.isEmpty()) {
			throw new IllegalArgumentException("Frame list is empty or null.");
		}

		ImageWriter gifWriter = ImageIO.getImageWritersByFormatName("gif").next();
		ImageOutputStream ios = new FileImageOutputStream(output);
		gifWriter.setOutput(ios);
		gifWriter.prepareWriteSequence(null);

		int delayTime = 10; // Time between frames in hundredths of a second (e.g., 10 = 0.1 second)

		for (int i = 0; i < frames.size(); i++) {
			BufferedImage img = frames.get(i);
			ImageWriteParam param = gifWriter.getDefaultWriteParam();
			IIOMetadata metadata = gifWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromBufferedImageType(img.getType()), param);

			// Set the delay time and loop setting
			String metaFormatName = metadata.getNativeMetadataFormatName();
			IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

			// Configure the GraphicsControlExtension node
			IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
			graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delayTime));
			graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
			graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
			graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
			graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

			// Configure the ApplicationExtensions node to make the GIF loop infinitely
			if (i == 0) {
				IIOMetadataNode appExtensionsNode = getNode(root, "ApplicationExtensions");
				IIOMetadataNode appExtensionNode = new IIOMetadataNode("ApplicationExtension");

				appExtensionNode.setAttribute("applicationID", "NETSCAPE");
				appExtensionNode.setAttribute("authenticationCode", "2.0");

				byte[] appExtensionBytes = new byte[]{
						0x1, // Sub-block index (always 1)
						0x0, 0x0 // Loop count (0 means infinite loop)
				};
				appExtensionNode.setUserObject(appExtensionBytes);
				appExtensionsNode.appendChild(appExtensionNode);
			}

			metadata.setFromTree(metaFormatName, root);

			IIOImage frame = new IIOImage(img, null, metadata);
			gifWriter.writeToSequence(frame, param);
		}

		gifWriter.endWriteSequence();
		ios.close();
	}

	/**
	 * Retrieves or creates a child node with the specified name.
	 *
	 * @param rootNode The parent node.
	 * @param nodeName The name of the child node to retrieve.
	 * @return The retrieved or newly created child node.
	 */
	private IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
		for (int i = 0; i < rootNode.getLength(); i++) {
			if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
				return (IIOMetadataNode) rootNode.item(i);
			}
		}
		IIOMetadataNode node = new IIOMetadataNode(nodeName);
		rootNode.appendChild(node);
		return node;
	}
}
