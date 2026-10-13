package com.campus.powerbank.service;

import com.campus.powerbank.common.BizException;
import com.campus.powerbank.common.ErrorCode;
import com.campus.powerbank.model.Member;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块一：会员服务。
 *
 * <p>负责会员注册、查询、充值与等级判定。对外提供 {@link #register}、
 * {@link #findById}、{@link #recharge}、{@link #listAll} 等接口，
 * 供租借模块调用，构成模块间的接口交互与数据传递。</p>
 */
public class MemberService {

    /** 会员 id 自增序列。 */
    private long idGenerator = 1000L;

    /** 会员存储：LinkedHashMap 保证注册顺序，便于结果断言。 */
    private final Map<Long, Member> members = new LinkedHashMap<>();

    /** 手机号索引，用于查重。 */
    private final Map<String, Long> phoneIndex = new LinkedHashMap<>();

    /**
     * 注册会员。
     *
     * @param phone 手机号，11 位且首位为 1
     * @param name  姓名
     * @param age   年龄，有效范围 [18, 60]
     * @return 新注册的会员
     * @throws BizException 参数非法或手机号已存在
     */
    public Member register(String phone, String name, int age) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_NULL, "姓名不能为空");
        }
        Member.validatePhone(phone);
        // 分支：手机号查重
        if (phoneIndex.containsKey(phone)) {
            throw new BizException(ErrorCode.PHONE_DUPLICATED, phone);
        }
        long id = ++idGenerator;
        Member member = new Member(id, phone, name, age);
        members.put(id, member);
        phoneIndex.put(phone, id);
        return member;
    }

    /**
     * 按 id 查询会员。
     *
     * @param id 会员 id
     * @return 会员
     * @throws BizException 会员不存在
     */
    public Member findById(long id) {
        Member member = members.get(id);
        if (member == null) {
            throw new BizException(ErrorCode.MEMBER_NOT_FOUND, "id=" + id);
        }
        return member;
    }

    /**
     * 会员充值，同时触发等级重新判定。
     *
     * @param memberId 会员 id
     * @param amount   单次充值金额，有效范围 [1, 2000]
     * @return 充值后的会员
     * @throws BizException 会员不存在或金额越界
     */
    public Member recharge(long memberId, int amount) {
        Member member = findById(memberId);
        member.recharge(amount);
        return member;
    }

    /**
     * 修改信用分（多条件：>= 800 免押金、>= 500 允许租借）。
     *
     * @param memberId 会员 id
     * @param credit   信用分，有效范围 [0, 1000]
     * @return 修改后的会员
     */
    public Member updateCredit(long memberId, int credit) {
        Member member = findById(memberId);
        member.setCredit(credit);
        return member;
    }

    /** 返回全部会员（只读副本列表）。 */
    public List<Member> listAll() {
        return new ArrayList<>(members.values());
    }

    /** 清空数据，便于测试用例之间隔离。 */
    public void clear() {
        members.clear();
        phoneIndex.clear();
        idGenerator = 1000L;
    }

    /** 当前会员数量。 */
    public int size() {
        return members.size();
    }
}
