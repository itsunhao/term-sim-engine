package com.termgame.model.state;

/**
 * 员工雇佣状态。
 * salary: 当前月薪（涨薪/降薪后更新）
 * raiseLevel: 涨薪次数（效率 +15%/次）
 * cutLevel: 降薪次数（效率 -15%/次；≥2 次后每日有离职风险）
 * sinceDay: 雇佣时的全局天数（展示用）
 */
public class StaffState {

    private String staffId;
    private double salary;
    private int raiseLevel;
    private int cutLevel;
    private int sinceDay;
    // ---- v1.11.0 员工培养晋升 ----
    /** 累计工作经验（每日结算 + expPerDay，成功补货/进货 +expPerAction） */
    private int exp;
    /** 当前等级 1~3（升级效率 +5%/级） */
    private int level = 1;
    /** 忠诚度 0~100（发薪/涨薪/红包提升，欠薪/降薪降低；<20 有离职风险） */
    private double loyalty = 50;
    /** 已学技能 id（晋升后选择） */
    private String skillId;
    /** 是否已晋升（晋升后效率 +10%，且有店长光环） */
    private boolean promoted;

    public StaffState() {
    }

    public StaffState(String staffId, double salary, int raiseLevel, int cutLevel, int sinceDay) {
        this.staffId = staffId;
        this.salary = salary;
        this.raiseLevel = raiseLevel;
        this.cutLevel = cutLevel;
        this.sinceDay = sinceDay;
    }

    /** 效率倍率：涨降薪 ±15%/次 + 等级 5%/级 + 晋升 +10%，范围 0.55 ~ 1.90 */
    public double efficiency() {
        double e = 1 + (raiseLevel - cutLevel) * 0.15 + (level - 1) * 0.05 + (promoted ? 0.10 : 0.0);
        return Math.max(0.55, Math.min(1.90, e));
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getLoyalty() {
        return loyalty;
    }

    public void setLoyalty(double loyalty) {
        this.loyalty = Math.max(0, Math.min(100, loyalty));
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public boolean isPromoted() {
        return promoted;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    public double getSalary() {
        return salary;
    }

    public void setSalary(double salary) {
        this.salary = salary;
    }

    public int getRaiseLevel() {
        return raiseLevel;
    }

    public void setRaiseLevel(int raiseLevel) {
        this.raiseLevel = raiseLevel;
    }

    public int getCutLevel() {
        return cutLevel;
    }

    public void setCutLevel(int cutLevel) {
        this.cutLevel = cutLevel;
    }

    public int getSinceDay() {
        return sinceDay;
    }

    public void setSinceDay(int sinceDay) {
        this.sinceDay = sinceDay;
    }
}
