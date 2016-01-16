package com.wilutions.itol;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.wilutions.itol.db.Attachment;
import com.wilutions.itol.db.IdName;
import com.wilutions.itol.db.Issue;
import com.wilutions.itol.db.ProgressCallback;
import com.wilutions.itol.db.Property;
import com.wilutions.mslib.outlook.OlSaveAsType;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public class AttachmentHelper {

	private IssueMailItem mailItem;
	private List<Runnable> resourcesToRelease = new ArrayList<Runnable>();
	private File __tempDir;

	public AttachmentHelper() {
	}

	public void initialUpdate(IssueMailItem mailItem, Issue issue) throws IOException {
		releaseResources();

		this.mailItem = mailItem;

		if (issue != null) {
			boolean isNew = issue.getId().isEmpty();
			String newNotes = issue.getPropertyString(Property.NOTES, "");
			if (isNew || newNotes.length() != 0) {
				initialUpdateNewIssueAttachments(issue);
			}
		}
	}

	private File getTempDir() {
		if (__tempDir == null) {
			__tempDir = new File(Globals.getTempDir(), Long.toString(System.currentTimeMillis()));
			__tempDir.mkdirs();
		}
		return __tempDir;
	}

	private void initialUpdateNewIssueAttachments(Issue issue) throws IOException {

		if (mailItem.getBody().length() != 0) {
			String ext = getConfigMsgFileExt(MsgFileTypes.NOTHING);

			if (!ext.equals(MsgFileTypes.NOTHING.getId())) {

				MailAtt mailAtt = new MailAtt(mailItem, ext);
				issue.getAttachments().add(mailAtt);

				OlSaveAsType saveAsType = MsgFileTypes.getMsgFileType(ext);

				if (!MsgFileTypes.isContainerFormat(saveAsType)) {
					IssueAttachments mailAtts = mailItem.getAttachments();
					int n = mailAtts.getCount();
					for (int i = 1; i <= n; i++) {
						com.wilutions.mslib.outlook.Attachment matt = mailAtts.getItem(i);
						MailAttAtt attatt = new MailAttAtt(matt);
						issue.getAttachments().add(attatt);
					}
				}
			}
		}
	}

	public Attachment makeMailAttachment(IssueMailItem mailItem) throws IOException {
		String ext = getConfigMsgFileExt(MsgFileTypes.NOTHING);
		MailAtt mailAtt = new MailAtt(mailItem, ext);
		return mailAtt;
	}

	public void releaseResources() {
		for (Runnable run : resourcesToRelease) {
			try {
				run.run();
			}
			catch (Throwable ignored) {
			}
		}
		if (__tempDir != null) {
			__tempDir.delete();
			__tempDir = null;
		}
	}

	public static Attachment createFromFile(File file) {
		return new FileAtt(file);
	}

	public Attachment createFromImage(Image image, String optionalFileName) {
		String fileName = optionalFileName;
		if (fileName == null || fileName.isEmpty()) {
			String name = mailItem.getSubject() + "-" + image.getWidth() + "x" + image.getHeight();
			fileName = MsgFileTypes.makeValidFileName(name) + ".jpg";
		}
		File file = new File(getTempDir(), fileName);
		try {
			file.delete();
			BufferedImage bimage = SwingFXUtils.fromFXImage(image, null); // Get
																			// buffered
																			// image.
			BufferedImage imageRGB = new BufferedImage(bimage.getWidth(), bimage.getHeight(), BufferedImage.OPAQUE); // Remove
																														// alpha-channel
																														// from
																														// buffered
																														// image.
			Graphics2D graphics = imageRGB.createGraphics();
			graphics.drawImage(bimage, 0, 0, null);
			ImageIO.write(imageRGB, "jpg", file);
			graphics.dispose();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return new FileAtt(file);
	}

	public static String getFileUrl(File file) {
		String url = file.getAbsolutePath();
		url = url.replace("\\", "/");
		return "file:///" + url;
	}

	public static String getFileName(String path) {
		String fname = path;
		if (path != null && path.length() != 0) {
			int p = path.lastIndexOf(File.separatorChar);
			fname = path.substring(p + 1);
		}
		return fname;
	}

	public static String getFileNameWithoutExt(String path) {
		String fname = path;
		if (path != null && path.length() != 0) {
			int p = path.lastIndexOf(File.separatorChar);
			fname = path.substring(p + 1);
			p = fname.lastIndexOf('.');
			if (p >= 0) {
				fname = fname.substring(0, p);
			}
		}
		return fname;
	}

	public static String getFileExt(String path) {
		String ext = "";
		if (path != null && path.length() != 0) {
			int p = path.lastIndexOf('.');
			if (p >= 0) {
				ext = path.substring(p + 1).toLowerCase();
			}
		}
		return ext;
	}

	public static String getFileContentType(String fname) {
		String ext = ".";
		int p = fname.lastIndexOf('.');
		if (p >= 0) {
			ext = fname.substring(p);
		}
		return ContentTypes.getContentType(ext.toLowerCase());
	}

	public static String makeAttachmentSizeString(long contentLength) {
		String ret = "";
		if (contentLength >= 0) {
			final String[] dims = new String[] { "Bytes", "KB", "MB", "GB", "TB" };
			int dimIdx = 0;
			long c = contentLength, nb = 0;
			for (int i = 0; i < dims.length; i++) {
				nb = c;
				c = (long) Math.floor(c / 1000);
				if (c == 0) {
					dimIdx = i;
					break;
				}
			}
			ret = nb + " " + dims[dimIdx];
		}
		return ret;
	}

	/**
	 * Issue attachment for mail attachment.
	 */
	public class MailAttAtt extends Attachment {

		private final com.wilutions.mslib.outlook.Attachment matt;

		private MailAttAtt(com.wilutions.mslib.outlook.Attachment matt) {
			this.matt = matt;
			File mattFile = new File(getTempDir(), matt.getFileName());
			super.setSubject(matt.getFileName());
			super.setContentType(getFileContentType(mattFile.getName()));
			super.setContentLength(matt.getSize());
			super.setFileName(mattFile.getAbsolutePath());
		}

		@Override
		public InputStream getStream() {
			File f = save();
			try {
				return new FileInputStream(f);
			}
			catch (FileNotFoundException e) {
				return null;
			}
		}

		@Override
		public String getUrl() {
			save();
			return super.getUrl();
		}

		private File save() {
			final File mattFile = new File(getFileName());
			if (!mattFile.exists()) {
				resourcesToRelease.add(() -> mattFile.delete());
				System.out.println("save attachment to " + mattFile);
				matt.SaveAsFile(mattFile.getAbsolutePath());
				super.setContentLength(mattFile.length());
				super.setUrl(getFileUrl(mattFile));
			}
			return mattFile;
		}
	}

	/**
	 * Issue attachment for mail. Must be public, otherwise it cannot be viewed
	 * in the table.
	 */
	public class MailAtt extends Attachment {

		private final IssueMailItem mailItem;
		private final String ext;

		private MailAtt(IssueMailItem mailItem, String ext) {
			this.mailItem = mailItem;
			this.ext = ext;

			OlSaveAsType saveAsType = MsgFileTypes.getMsgFileType(ext);
			String msgFileName = MsgFileTypes.makeMsgFileName(mailItem.getSubject(), saveAsType);
			File msgFile = new File(getTempDir(), msgFileName);

			super.setSubject(mailItem.getSubject());
			super.setContentType(getFileContentType(msgFileName));
			super.setContentLength(-1);
			super.setFileName(msgFile.getAbsolutePath());
		}

		@Override
		public InputStream getStream() {
			File f = save();
			try {
				return new FileInputStream(f);
			}
			catch (FileNotFoundException e) {
				return null;
			}
		}

		@Override
		public String getUrl() {
			save();
			return super.getUrl();
		}

		@Override
		public long getContentLength() {
			return super.getContentLength();
		}

		private File save() {

			final File msgFile = new File(getFileName());
			try {

				if (getContentLength() < 0) {

					OlSaveAsType saveAsType = MsgFileTypes.getMsgFileType(ext);

					resourcesToRelease.add(() -> msgFile.delete());

					System.out.println("save mail to " + msgFile);
					mailItem.SaveAs(msgFile.getAbsolutePath(), saveAsType);

					super.setContentLength(msgFile.length());
					super.setUrl(getFileUrl(msgFile));
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			return msgFile;
		}
	}

	/**
	 * Issue attachment for arbitrary file.
	 */
	public static class FileAtt extends Attachment {
		private File file;

		private FileAtt(File file) {
			this.file = file;
			super.setFileName(AttachmentHelper.getFileName(file.getAbsolutePath()));
			super.setContentLength(file.length());
			String url = getFileUrl(file);
			super.setUrl(url);
		}

		@Override
		public InputStream getStream() {
			InputStream ret = null;
			try {
				ret = new FileInputStream(file);
			}
			catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			return ret;
		}

	}

	private static String getConfigMsgFileExt(IdName defaultMsgFileExt) {
		String ext = Globals.getConfigPropertyString(Property.MSG_FILE_TYPE, defaultMsgFileExt.getId());
		return ext;
	}

	public void showAttachment(Attachment att, ProgressCallback cb) throws IOException {
		String url = att.getUrl();
		if (!url.startsWith("file:")) {
			url = internalDownloadAttachment(att, cb);
		}
		IssueApplication.showDocument(url);
	}

	private String internalDownloadAttachment(Attachment att, ProgressCallback cb) {
		String url = att.getUrl();
		try {
			String fileName = Globals.getIssueService().downloadAttachment(url, cb);
			File srcFile = new File(fileName);
			if (srcFile.exists()) {
				File destFile = new File(getTempDir(), att.getFileName());
				destFile.delete();
				if (srcFile.renameTo(destFile)) {
					fileName = destFile.getAbsolutePath();
					url = fileName;
				}
			}
		}
		catch (IOException e) {
		}
		return url;
	}
}