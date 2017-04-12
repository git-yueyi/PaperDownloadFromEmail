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
			// 1. 设置连接信息, 生成一个 Session
			Properties props = new Properties();

			props.setProperty("mail.smtp.host", "smtp.genomics.cn");
			props.setProperty("mail.smtp.auth", "true");
			Session session = Session.getDefaultInstance(props);
			// 2. 获取 Store 并连接到服务器
			//
			URLName urlname = new URLName("pop3", "pop.163.com", 110, null, "yueyi_mail@163.com", "******");
			Store store = session.getStore(urlname);
			store.connect();
			Folder folder = store.getDefaultFolder();// 默认父目录
			if (folder == null) {
				System.out.println("服务器不可用");
				return;
			}
			/*
			 * System.out.println("默认信箱名:" + folder.getName());
			 * 
			 * Folder[] folders = folder.list();// 默认目录列表 for(int i = 0; i <
			 * folders.length; i++) { System.out.println(folders[0].getName());
			 * } System.out.println("默认目录下的子目录数: " + folders.length);
			 */
			Folder popFolder = folder.getFolder("INBOX");// 获取收件箱
			popFolder.open(Folder.READ_ONLY);// 可读邮件,可以删邮件的模式打开目录
			// 4. 列出来收件箱 下所有邮件
			Message[] messages = popFolder.getMessages();

			// 取出来邮件数
			int msgCount = popFolder.getMessageCount();
			System.out.println("共有邮件: " + msgCount + "封");
			// FetchProfile fProfile = new FetchProfile();// 选择邮件的下载模式,
			// 根据网速选择不同的模式
			// fProfile.add(FetchProfile.Item.ENVELOPE);
			// folder.fetch(messages, fProfile);// 选择性的下载邮件
			// 5. 循环处理每个邮件并实现邮件转为新闻的功能
			for (int i = 0; i < msgCount; i++) {

				// 得到邮件的附件内容
				mailReceiver(messages[i]);
			}
			// 7. 关闭 Folder 会真正删除邮件, false 不删除
			popFolder.close(false);
			// 8. 关闭 store, 断开网络连接
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
	 * 解析邮件
	 * 
	 * @param messages
	 *            邮件对象
	 * @param i
	 * @return
	 * @throws IOException
	 * @throws MessagingException
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 */
	private void mailReceiver(Message msg) throws Exception {
		// 发件人信息
		String date = mDateFormatter.format(msg.getSentDate());
		System.out.println("邮件发送时间: " + date + "\t主题:" + msg.getSubject());

		// getContent() 是获取包裹内容, Part相当于外包装
		Object o = msg.getContent();

		// 得到该邮件的所有附件
		mAttchesOfCurrentMail.clear();
		detectAttach(o);

		// 下载所有附件
		for (String key : mAttchesOfCurrentMail.keySet()) {
			Part attchPart = mAttchesOfCurrentMail.get(key);

			// 去重
			// 判断是否存在
			String isExst = mSuccessDownloadMap.getOrDefault(key, "");
			if (isExst.isEmpty() || isExst.compareTo(date) < 0) {
				// 不存在 或者 有更新 就下载
				boolean isDownloadSuccess = dowload(attchPart);

				if (!isDownloadSuccess)
					continue;

				//changeModifyTime(mOutputPathStr + key, date);

				// 写入Log文件
				LogUtils.writeLine(mLogFileName, key + "\t" + date);

				mSuccessDownloadMap.put(key, date);

			}
		}

		// TODO 画一下邮件的结构，然后作笔记！！！
		// 然后代码上传gitHub
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
				System.out.println("修改失败！");
				return false;
			}
		}
		return true;
	}

	private boolean dowload(Part part) {
		try {
			if (part.getDisposition() != null) {

				String strFileNmae = MimeUtility.decodeText(part.getFileName()); // MimeUtility.decodeText解决附件名乱码问题

				InputStream in = part.getInputStream();// 打开附件的输入流

				// 读取附件字节并存储到文件中, 多个版本的文件会覆盖
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
		System.out.println("下载失败");
		return false;
	}

	private void detectAttach(Object obj)
			throws UnsupportedEncodingException, FileNotFoundException, MessagingException, IOException {

		if (obj instanceof Multipart) {
			Multipart multipart = (Multipart) obj;
			// System.out.println("邮件共有" + multipart.getCount() + "部分组成");
			// 依次处理各个部分
			for (int j = 0, n = multipart.getCount(); j < n; j++) {
				// System.out.println("处理第" + j + "部分");
				Part partJ = multipart.getBodyPart(j);// 解包, 取出 MultiPart的各个部分,
														// 每部分可能是邮件内容,
				// 也可能是另一个小包裹(MultipPart)
				// 判断此包裹内容是不是一个小包裹, 一般这一部分是 正文 Content-Type:
				// multipart/alternative
				if (partJ.getContent() instanceof Multipart) {
					Multipart p = (Multipart) partJ.getContent();// 转成小包裹
					detectAttach(p);

				}
				detectAttach(partJ);
			}
		} else if (obj instanceof Part) {
			Part partO = (Part) obj;
			if (partO.getDisposition() != null) {
				String strFileNmae = MimeUtility.decodeText(partO.getFileName()); // MimeUtility.decodeText解决附件名乱码问题

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
			mOutputPathStr = "C:\\Users\\gavinyue\\Desktop\\附件集合_1";
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
			System.out.println("没有历史日志文件");
			// e.printStackTrace();
		} catch (IOException e) {
			System.out.println("文件读写错误");
			e.printStackTrace();
		}

	}
}
