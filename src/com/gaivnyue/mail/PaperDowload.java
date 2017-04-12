package com.gaivnyue.mail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.MimeUtility;

import com.gaivnyue.utils.LogUtils;

public class PaperDowload {

	private static String mOutputPathStr = "";

	private static Map<String, String> mSuccessDownloadMap = new HashMap<String, String>();

	private static SimpleDateFormat mDateFormatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");

	private static Map<String, Part> mAttchesOfCurrentMail = new HashMap<String, Part>();

	private static String mLogFileName;

	// String regEx =
	// "[a-zA-Z_]{1,}[0-9]{0,}@(([a-zA-z0-9]-*){1,}\\.){1,3}[a-zA-z\\-]{1,}";

	public PaperDowload() {
		try {
			// 1. ����������Ϣ, ����һ�� Session
			Properties props = new Properties();

			props.setProperty("mail.smtp.host", "smtp.genomics.cn");
			props.setProperty("mail.smtp.auth", "true");
			Session session = Session.getDefaultInstance(props);
			// 2. ��ȡ Store �����ӵ�������
			//
			URLName urlname = new URLName("pop3", "pop.163.com", 110, null, "yueyi_mail@163.com", "******");
			Store store = session.getStore(urlname);
			store.connect();
			Folder folder = store.getDefaultFolder();// Ĭ�ϸ�Ŀ¼
			if (folder == null) {
				System.out.println("������������");
				return;
			}
			/*
			 * System.out.println("Ĭ��������:" + folder.getName());
			 * 
			 * Folder[] folders = folder.list();// Ĭ��Ŀ¼�б� for(int i = 0; i <
			 * folders.length; i++) { System.out.println(folders[0].getName());
			 * } System.out.println("Ĭ��Ŀ¼�µ���Ŀ¼��: " + folders.length);
			 */
			Folder popFolder = folder.getFolder("INBOX");// ��ȡ�ռ���
			popFolder.open(Folder.READ_ONLY);// �ɶ��ʼ�,����ɾ�ʼ���ģʽ��Ŀ¼
			// 4. �г����ռ��� �������ʼ�
			Message[] messages = popFolder.getMessages();

			// ȡ�����ʼ���
			int msgCount = popFolder.getMessageCount();
			System.out.println("�����ʼ�: " + msgCount + "��");
			// FetchProfile fProfile = new FetchProfile();// ѡ���ʼ�������ģʽ,
			// ��������ѡ��ͬ��ģʽ
			// fProfile.add(FetchProfile.Item.ENVELOPE);
			// folder.fetch(messages, fProfile);// ѡ���Ե������ʼ�
			// 5. ѭ������ÿ���ʼ���ʵ���ʼ�תΪ���ŵĹ���
			for (int i = 0; i < msgCount; i++) {

				// �õ��ʼ��ĸ�������
				mailReceiver(messages[i]);
			}
			// 7. �ر� Folder ������ɾ���ʼ�, false ��ɾ��
			popFolder.close(false);
			// 8. �ر� store, �Ͽ���������
			store.close();
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * �����ʼ�
	 * 
	 * @param messages
	 *            �ʼ�����
	 * @param i
	 * @return
	 * @throws IOException
	 * @throws MessagingException
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	private void mailReceiver(Message msg) throws Exception {
		// ��������Ϣ
		String date = mDateFormatter.format(msg.getSentDate());
		System.out.println("�ʼ�����ʱ��: " + date + "\t����:" + msg.getSubject());

		// getContent() �ǻ�ȡ��������, Part�൱�����װ
		Object o = msg.getContent();

		// �õ����ʼ������и���
		mAttchesOfCurrentMail.clear();
		detectAttach(o);

		// �������и���
		for (String key : mAttchesOfCurrentMail.keySet()) {
			Part attchPart = mAttchesOfCurrentMail.get(key);

			// ȥ��
			// �ж��Ƿ����
			String isExst = mSuccessDownloadMap.getOrDefault(key, "");
			if (isExst.isEmpty() || isExst.compareTo(date) < 0) {
				// ������ ���� �и��� ������
				boolean isDownloadSuccess = dowload(attchPart);

				if (!isDownloadSuccess)
					continue;

				//changeModifyTime(mOutputPathStr + key, date);

				// д��Log�ļ�
				LogUtils.writeLine(mLogFileName, key + "\t" + date);

				mSuccessDownloadMap.put(key, date);

			}
		}

		// TODO ��һ���ʼ��Ľṹ��Ȼ�����ʼǣ�����
		// Ȼ������ϴ�gitHub
	}

	private boolean changeModifyTime(String fileName, String date) {
		Long modifyTime = null;
		try {
			modifyTime = mDateFormatter.parse(date).getTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if (modifyTime != null) {
			boolean isModified = new File(mOutputPathStr + fileName).setLastModified(modifyTime);
			if (!isModified) {
				System.out.println("�޸�ʧ�ܣ�");
				return false;
			}
		}
		return true;
	}

	private boolean dowload(Part part) {
		try {
			if (part.getDisposition() != null) {

				String strFileNmae = MimeUtility.decodeText(part.getFileName()); // MimeUtility.decodeText�����������������

				InputStream in = part.getInputStream();// �򿪸�����������

				// ��ȡ�����ֽڲ��洢���ļ���, ����汾���ļ��Ḳ��
				java.io.FileOutputStream out = new FileOutputStream(mOutputPathStr + "\\"+strFileNmae, false);

				int data;
				while ((data = in.read()) != -1) {
					out.write(data);
				}
				in.close();
				out.close();
				return true;
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("����ʧ��");
		return false;
	}

	private void detectAttach(Object obj)
			throws UnsupportedEncodingException, FileNotFoundException, MessagingException, IOException {

		if (obj instanceof Multipart) {
			Multipart multipart = (Multipart) obj;
			// System.out.println("�ʼ�����" + multipart.getCount() + "�������");
			// ���δ����������
			for (int j = 0, n = multipart.getCount(); j < n; j++) {
				// System.out.println("�����" + j + "����");
				Part partJ = multipart.getBodyPart(j);// ���, ȡ�� MultiPart�ĸ�������,
														// ÿ���ֿ������ʼ�����,
				// Ҳ��������һ��С����(MultipPart)
				// �жϴ˰��������ǲ���һ��С����, һ����һ������ ���� Content-Type:
				// multipart/alternative
				if (partJ.getContent() instanceof Multipart) {
					Multipart p = (Multipart) partJ.getContent();// ת��С����
					detectAttach(p);

				}
				detectAttach(partJ);
			}
		} else if (obj instanceof Part) {
			Part partO = (Part) obj;
			if (partO.getDisposition() != null) {
				String strFileNmae = MimeUtility.decodeText(partO.getFileName()); // MimeUtility.decodeText�����������������

				if (strFileNmae != null) {
					mAttchesOfCurrentMail.put(strFileNmae, partO);
					System.out.println("-------------------------------" + strFileNmae);
				}
			}
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length == 0) {
			mOutputPathStr = "C:\\Users\\gavinyue\\Desktop\\��������_1";
		} else {
			mOutputPathStr = args[1];
			System.out.println(args.toString());
		}
		mLogFileName = mOutputPathStr + "\\downloadLog.txt";
		initDownd(mLogFileName);

		for (int i = 0; i < mSuccessDownloadMap.size(); i++) {
			System.out.println(mSuccessDownloadMap.toString());
		}

		new PaperDowload();
		return;
	}

	private static void initDownd(String logFileName) {
		// TODO Auto-generated method stub
		BufferedReader bf;
		try {
			bf = new BufferedReader(new FileReader(logFileName));
			String line;
			while ((line = bf.readLine()) != null) {
				String[] strs = line.split("\t");
				if (strs.length == 2) {
					mSuccessDownloadMap.put(strs[0], strs[1]);
				}
			}

		} catch (FileNotFoundException e) {
			System.out.println("û����ʷ��־�ļ�");
			// e.printStackTrace();
		} catch (IOException e) {
			System.out.println("�ļ���д����");
			e.printStackTrace();
		}

	}
}
