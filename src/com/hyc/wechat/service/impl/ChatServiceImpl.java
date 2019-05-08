/*
 * Copyright (c) 2019.  黄钰朝
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hyc.wechat.service.impl;

import com.hyc.wechat.dao.ChatDao;
import com.hyc.wechat.dao.MemberDao;
import com.hyc.wechat.dao.MessageDao;
import com.hyc.wechat.dao.UserDao;
import com.hyc.wechat.exception.DaoException;
import com.hyc.wechat.factory.DaoProxyFactory;
import com.hyc.wechat.model.dto.ServiceResult;
import com.hyc.wechat.model.po.Chat;
import com.hyc.wechat.model.po.Member;
import com.hyc.wechat.model.po.User;
import com.hyc.wechat.service.ChatService;
import com.hyc.wechat.service.constants.ServiceMessage;
import com.hyc.wechat.service.constants.Status;

import java.util.LinkedList;
import java.util.List;

import static com.hyc.wechat.util.UUIDUtils.getUUID;

/**
 * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
 * @description 负责提供聊天相关服务
 * @date 2019-05-03 13:52
 */
public class ChatServiceImpl implements ChatService {

    UserDao userDao = (UserDao) DaoProxyFactory.getInstance().getProxyInstance(UserDao.class);
    ChatDao chatDao = (ChatDao) DaoProxyFactory.getInstance().getProxyInstance(ChatDao.class);
    MemberDao memberDao = (MemberDao) DaoProxyFactory.getInstance().getProxyInstance(MemberDao.class);
    MessageDao messageDao = (MessageDao) DaoProxyFactory.getInstance().getProxyInstance(MessageDao.class);

    /**
     * 创建一个聊天
     *
     * @param chat 要创建的聊天对象
     * @return 返回传入的聊天对象
     * @name createChat
     * @notice none
     * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
     * @date 2019/5/3
     */
    @Override
    public ServiceResult createChat(Chat chat) {
        try {
            //阻止插入id
            chat.setId(null);
            //如果没有设置唯一标识群号，则使用uuid
            if (chat.getNumber() == null) {
                chat.setNumber(getUUID());
            }
            //检查创建用户是否存在
            if (userDao.getUserById(chat.getOwnerId()) == null) {
                return new ServiceResult(Status.ERROR, ServiceMessage.ACCOUNT_NOT_FOUND.message, chat);
            }
            //将该聊天插入数据库
            if (chatDao.insert(chat) != 1) {
                return new ServiceResult(Status.ERROR, ServiceMessage.CREATE_CHAT_FAILED.message, chat);
            }
            //插入成功将其从数据库查出，以便返回id
            chat=chatDao.getByChatNumber(chat.getNumber());
        } catch (DaoException e) {
            e.printStackTrace();
            return new ServiceResult(Status.ERROR, ServiceMessage.DATABASE_ERROR.message, chat);
        }
        return new ServiceResult(Status.SUCCESS, ServiceMessage.CREATE_CHAT_SUCCESS.message, chat);
    }

    /**
     * 把用户添加到聊天中
     *
     * @param members 要添加的成员对象
     * @return 返回传入的成员对象
     * @name joinChat
     * @notice none
     * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
     * @date 2019/5/3
     */
    @Override
    public ServiceResult joinChat(Member[] members) {
        try {
            for (Member member : members) {
                //阻止插入id
                member.setId(null);
                Chat chat =chatDao.getChatById(member.getChatId());
                //检查聊天是否存在
                if ( chat== null) {
                    return new ServiceResult(Status.ERROR, ServiceMessage.CHAT_NOT_FOUND.message, members);
                }
                //检查用户是否存在
                if (userDao.getUserById(member.getUserId()) == null) {
                    return new ServiceResult(Status.ERROR, ServiceMessage.ACCOUNT_NOT_FOUND.message, members);
                }
                //检查该用户是否已在此聊天
                if (memberDao.getMemberByUIdAndChatId(member.getUserId(), member.getChatId()) != null) {
                    return new ServiceResult(Status.ERROR, ServiceMessage.MEMBER_ALREADY_EXIST.message, members);
                }
                //将该成员插入到此聊天
                if (memberDao.insert(member) != 1) {
                    return new ServiceResult(Status.ERROR, ServiceMessage.JOIN_CHAT_FAILED.message, members);
                }
                //将该聊天的成员加一
                chat.setMember(chat.getMember()+1);
                chatDao.update(chat);

            }
        } catch (DaoException e) {
            e.printStackTrace();
            return new ServiceResult(Status.ERROR, ServiceMessage.DATABASE_ERROR.message, members);
        }
        return new ServiceResult(Status.SUCCESS, ServiceMessage.JOIN_CHAT_SUCCESS.message, members);
    }

    /**
     * 把成员从聊天中移除，如果该成员是聊天的最后一个成员</br>
     * 就将该聊天一并删除
     *
     * @param members 要移除的成员对象
     * @return 返回移除的成员对象
     * @name quitChat
     * @notice none
     * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
     * @date 2019/5/3
     */
    @Override
    public ServiceResult quitChat(Member[] members) {
        try {
            for (Member member : members) {
                //检查成员是否存在
                if (memberDao.getMemberByUIdAndChatId(member.getUserId(), member.getChatId()) == null) {
                    return new ServiceResult(Status.ERROR, ServiceMessage.MEMBER_NOT_FOUND.message, members);
                }
                //将该成员从聊天中移除
                if (memberDao.delete(member) != 1) {
                    return new ServiceResult(Status.ERROR, ServiceMessage.QUIT_CHAT_FAILED.message, members);
                }
                //将该聊天的成员减一
                Chat chat =chatDao.getChatById(member.getChatId());
                chat.setMember(chat.getMember()-1);
                chatDao.update(chat);
            }
        } catch (DaoException e) {
            e.printStackTrace();
            return new ServiceResult(Status.ERROR, ServiceMessage.DATABASE_ERROR.message, members);
        }
        return new ServiceResult(Status.SUCCESS, ServiceMessage.QUIT_CHAT_SUCCESS.message, members);
    }


    /**
     * 返回一个用户的所有聊天
     *
     * @param user 用户对象
     * @return 该用户的所有聊天
     * @name listChat
     * @notice none
     * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
     * @date 2019/5/3
     */
    @Override
    public ServiceResult listChat(User user) {
        List list = null;
        List chats;
        try {
            list = chatDao.listByUserId(user.getId());
            if (list == null || list.size() == 0) {
                return new ServiceResult(Status.SUCCESS, ServiceMessage.NO_CHAT_NOW.message, list);
            }
            //将聊天头像设置未对方头像，如果是群聊则不用设置
            chats = new LinkedList();
            for (int i = 0; i < list.size(); i++) {
                Chat chat = (Chat) list.get(i);
                if (chat.getMember() == 2) {
                    chats.add(chatDao.toFriendChat(chat.getId(), user.getId()));
                } else {
                    chats.add(list.get(i));
                }
            }
        } catch (DaoException e) {
            e.printStackTrace();
            return new ServiceResult(Status.ERROR, ServiceMessage.DATABASE_ERROR.message, list);
        }
        return new ServiceResult(Status.SUCCESS, ServiceMessage.LIST_CHAT_SUCCESS.message, chats);
    }

    /**
     * 删除一个聊天
     *
     * @param chat
     * @return
     * @name removeChat
     * @notice none
     * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
     * @date 2019/5/6
     */
    @Override
    public void removeChat(Chat chat) {
        try {
            chatDao.delete(chat);
        }catch (DaoException e){
            e.printStackTrace();
        }
    }

    /**
     * 通过聊天编号查询聊天
     *
     * @param number 聊天编号
     * @return
     * @name getChatByNumber
     * @notice none
     * @author <a href="mailto:kobe524348@gmail.com">黄钰朝</a>
     * @date 2019/5/6
     */
    @Override
    public void getChatByNumber(Object number) {
        try {
            chatDao.getByChatNumber(number);
        }catch (DaoException e){
            e.printStackTrace();
        }
    }


}