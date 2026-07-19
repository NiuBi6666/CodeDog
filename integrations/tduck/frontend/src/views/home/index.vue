<template>
  <div class="home-container" :class="{ 'is-embedded': isEmbedded }">
    <!-- 顶部高奢毛玻璃导航栏 -->
    <div class="header-container">
      <div class="left-header">
        <div class="logo-wrapper" @click="$router.push({ path: '/' })">
          <img src="@/assets/images/logo-tduck-ce.svg" alt="TDUCK Logo" class="home-logo-img" />
        </div>
      </div>
      <div class="right-header">
        <!-- 高级玻璃拟态用户头像与下拉框 -->
        <el-popover placement="bottom-end" trigger="click" width="200" popper-class="premium-user-popover">
          <div class="user-person-menu">
            <div class="user-profile-summary" v-if="getUserInfo">
              <img :src="getUserInfo.avatar" class="popover-avatar" />
              <div class="popover-meta">
                <p class="nick-name">{{ getUserInfo.name || '未命名用户' }}</p>
                <span class="user-role-badge" :class="{ 'admin-badge': getUserInfo.admin }">
                  {{ getUserInfo.admin ? '系统管理员' : '官方会员' }}
                </span>
              </div>
            </div>
            <el-divider />
            <div class="person-menu-links">
              <div class="person-menu-item" @click="returnToCodeDog">
                <i class="el-icon-back" />
                <span>返回 CodeDog</span>
              </div>
            </div>
          </div>
          <div slot="reference" class="avatar-trigger-wrapper">
            <img v-if="getUserInfo" :src="getUserInfo.avatar" class="user-avatar" />
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 主体框架容器 -->
    <div class="content-container">
      <!-- 悬浮侧边栏 -->
      <div class="menu-box">
        <div class="menu-view">
          <div
            v-for="menu in menuList"
            v-show="!menu.admin || (menu.admin && getUserInfo && getUserInfo.admin)"
            :key="menu.route"
            :class="defaultActiveMenu === menu.route ? 'menu-item-active menu-item' : 'menu-item'"
            @click="menuClickHandle(menu)"
          >
            <font-icon :class="menu.icon" class="menu-icon" />
            <span class="menu-label">{{ menu.name }}</span>
          </div>
        </div>

        <!-- 侧边栏底部：登出和版权 -->
        <div class="menu-footer">
          <div class="logout-btn-wrapper">
            <button class="logout-action-btn" @click="returnToCodeDog">
              <i class="el-icon-back" />
              <span>返回 CodeDog</span>
            </button>
          </div>

          <div class="about-container">
            <span class="desc-text">
              <span>CodeDog · 问卷与作业</span>
              <span class="copyright-text">Powered by TDuck</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 右侧主展示区 -->
      <div class="view-container">
        <div class="router-view-wrapper">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import store from '@/store'
import router from '@/router'
import { createFormRequest } from '@/api/project/form'

export default {
  name: 'NewIndex',
  components: {},
  data() {
    return {
      defaultActiveMenu: '/home',
      isEmbedded: sessionStorage.getItem('codedogEmbedded') === '1',
      menuList: [
        {
          route: '/home',
          name: '我的项目',
          icon: 'fa-pencil-square'
        },
        {
          route: '/project/template',
          name: '共享模板',
          icon: 'fa-caret-square-o-up'
        },
        {
          route: '/manage/system',
          name: '系统配置',
          icon: 'el-icon-s-tools',
          admin: true
        },
        {
          route: '/project/template/category',
          name: '模板分类',
          icon: 'el-icon-bank-card',
          admin: true
        },
        {
          route: '/project/theme/index',
          name: '主题列表',
          icon: 'el-icon-view',
          admin: true
        },
        {
          route: '/project/theme/category',
          name: '主题分类',
          icon: 'el-icon-edit',
          admin: true
        },
        {
          route: '/project/recycle',
          name: '回收中心',
          icon: 'fa-trash'
        }
      ]
    }
  },
  computed: {
    getStore() {
      return store
    },
    getUserInfo() {
      try {
        return JSON.parse(this.getStore.getters['user/userInfo'])
      } catch (e) {
        return {}
      }
    }
  },
  created() {
    let user = {}
    try {
      user = JSON.parse(this.getStore.getters['user/userInfo'] || '{}')
    } catch (e) {
      user = {}
    }
    if (!user) {
      this.$router.push({
        path: '/login',
        query: {
          redirect: router.currentRoute.fullPath
        }
      })
    } else if (this.$route.path == '/') {
      // 将主路由 / 重定向到首页
      this.$router.push({ path: this.menuList[0].route })
    }
    if (this.$route.path) {
      this.defaultActiveMenu = this.$route.path
    }
    console.log(user)
  },
  methods: {
    menuClickHandle(menu) {
      this.$router.replace({ path: menu.route })
    },
    createBlankTemplate() {
      createFormRequest({
        description: '快来填写你的问卷描述吧',
        type: 'ORDINARY',
        name: '问卷名称'
      }).then((res) => {
        this.$router.push({
          path: '/project/form',
          query: { key: res.data.formKey }
        })
      })
    },
    returnToCodeDog() {
      window.top.location.href = 'https://codedog.online/index'
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/assets/styles/variables.scss';

/* 1. 主页面布局容器 */
.home-container {
  background-color: #f5f5f7; // 苹果招牌浅色偏冷灰
  display: flex;
  height: 100vh;
  width: 100vw;
  flex-direction: column;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}

/* 2. 顶部高奢毛玻璃导航栏 */
.header-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: rgba(255, 255, 255, 0.72);
  height: 60px;
  padding: 0 40px 0 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  backdrop-filter: blur(20px) saturate(190%);
  -webkit-backdrop-filter: blur(20px) saturate(190%);
  z-index: 100;
  flex-shrink: 0;

  .left-header {
    display: flex;
    align-items: center;
    width: 230px;
    justify-content: center;

    .logo-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      user-select: none;
      transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);

      &:hover {
        transform: scale(1.03);
        filter: drop-shadow(0 4px 12px rgba(51, 112, 255, 0.15));
      }

      .home-logo-img {
        width: auto;
        display: block;
      }
    }
  }

  .right-header {
    display: flex;
    align-items: center;

    .avatar-trigger-wrapper {
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2px;
      border-radius: 50%;
      border: 2px solid transparent;
      transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);

      &:hover {
        border-color: rgba(51, 112, 255, 0.2);
        transform: scale(1.05);
        .user-avatar {
          box-shadow: 0 4px 16px rgba(51, 112, 255, 0.15);
        }
      }
    }

    .user-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      object-fit: cover;
      transition: all 0.3s ease;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    }
  }
}

/* 3. 主体框架与悬浮侧边栏 */
.content-container {
  height: calc(100vh - 60px);
  overflow: hidden;
  display: flex;
  flex-direction: row;
  width: 100%;
}

.menu-box {
  width: 230px;
  flex-shrink: 0;
  display: flex;
  margin: 20px 0 20px 20px;
  flex-direction: column;
  background-color: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border: 1px solid rgba(0, 0, 0, 0.04);
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.015);
  padding: 16px 12px;
  box-sizing: border-box;

  .menu-item {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #515154;
    font-size: 14.5px;
    font-weight: 500;
    padding: 12px 16px;
    margin-bottom: 6px;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
    box-sizing: border-box;

    .menu-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      width: 20px;
      height: 20px;
      margin-right: 12px;
      color: #86868b;
      transition: all 0.25s ease;
    }

    .menu-label {
      letter-spacing: 0.3px;
      transition: transform 0.25s ease;
    }

    &:hover {
      background-color: rgba(0, 0, 0, 0.035);
      color: #1d1d1f;

      .menu-icon {
        color: #1d1d1f;
        transform: scale(1.1);
      }

      .menu-label {
        transform: translateX(2px);
      }
    }

    &.menu-item-active {
      background: linear-gradient(135deg, $color-primary 0%, #1d4ed8 100%) !important;
      color: #ffffff !important;
      box-shadow: 0 8px 20px rgba(51, 112, 255, 0.25);
      font-weight: 600;

      .menu-icon {
        color: #ffffff !important;
      }

      &:hover {
        .menu-label {
          transform: none;
        }
      }
    }
  }

  .menu-view {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow-y: auto;

    /* 隐藏滚动条 */
    &::-webkit-scrollbar {
      width: 0;
    }
  }

  .menu-footer {
    border-top: 1px solid rgba(0, 0, 0, 0.04);
    padding-top: 16px;
    margin-top: 8px;

    .logout-btn-wrapper {
      padding: 0 16px;
      margin-bottom: 16px;

      .logout-action-btn {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        background-color: #f1f2f5;
        color: #515154;
        border: 1px solid transparent;
        border-radius: 10px;
        padding: 9px 0;
        font-size: 13.5px;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
        font-family: inherit;
        outline: none;

        i {
          font-size: 15px;
          color: #86868b;
          transition: all 0.25s ease;
        }

        &:hover {
          background-color: rgba(255, 59, 48, 0.06);
          border-color: rgba(255, 59, 48, 0.15);
          color: #ff3b30;
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(255, 59, 48, 0.1);

          i {
            color: #ff3b30;
          }
        }

        &:active {
          transform: translateY(0);
        }
      }
    }

    .about-container {
      text-align: center;
      font-size: 11px;
      color: #86868b;
      font-weight: 400;

      a {
        color: #86868b;
        text-decoration: none;
        transition: color 0.2s ease;

        &:hover {
          color: $color-primary;
        }
      }

      .desc-text {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
        line-height: 1.2;
      }
    }
  }
}

/* 4. 右侧主展示视口 */
.view-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin: 20px;
  overflow: hidden;
  box-sizing: border-box;

  .router-view-wrapper {
    flex: 1;
    background-color: #ffffff;
    border-radius: 20px;
    border: 1px solid rgba(0, 0, 0, 0.04);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.015);
    overflow-y: auto;
    box-sizing: border-box;
  }
}

/* 6. 头像弹窗下拉框内部结构样式 */
.user-person-menu {
  display: flex;
  flex-direction: column;
  padding: 4px;

  .user-profile-summary {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 8px 12px 8px;

    .popover-avatar {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      object-fit: cover;
      border: 1.5px solid rgba(51, 112, 255, 0.1);
    }

    .popover-meta {
      display: flex;
      flex-direction: column;
      gap: 3px;

      .nick-name {
        margin: 0;
        font-size: 14.5px;
        font-weight: 600;
        color: #1d1d1f;
        letter-spacing: -0.2px;
      }

      .user-role-badge {
        font-size: 10px;
        font-weight: 600;
        color: #3b82f6;
        background: rgba(59, 130, 246, 0.08);
        padding: 2px 6px;
        border-radius: 6px;
        align-self: flex-start;

        &.admin-badge {
          color: #ff9500;
          background: rgba(255, 149, 0, 0.08);
        }
      }
    }
  }

  .person-menu-links {
    display: flex;
    flex-direction: column;

    .person-menu-item {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      border-radius: 8px;
      font-size: 13.5px;
      font-weight: 500;
      color: #515154;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      gap: 10px;

      i {
        font-size: 15px;
        color: #86868b;
        transition: color 0.2s ease;
      }

      &:hover {
        background-color: rgba(51, 112, 255, 0.06);
        color: $color-primary;

        i {
          color: $color-primary;
        }
      }

      &.logout-item:hover {
        background-color: rgba(255, 59, 48, 0.06);
        color: #ff3b30;

        i {
          color: #ff3b30;
        }
      }
    }

    .el-divider--horizontal {
      margin: 4px 0;
      background-color: rgba(0, 0, 0, 0.035);
    }
  }
}
</style>

<!-- 7. 全局覆盖 ElementUI 下拉弹窗样式，应用极致玻璃拟态 -->
<style lang="scss">
.premium-user-popover.el-popover {
  border-radius: 16px !important;
  border: 1px solid rgba(0, 0, 0, 0.04) !important;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06) !important;
  background: rgba(255, 255, 255, 0.85) !important;
  backdrop-filter: blur(20px) saturate(190%) !important;
  -webkit-backdrop-filter: blur(20px) saturate(190%) !important;
  padding: 12px 10px !important;

  /* 重置弹窗内部 ElementUI 的 divider */
  .el-divider--horizontal {
    margin: 8px 0 !important;
    background-color: rgba(0, 0, 0, 0.04) !important;
  }
}
</style>

<style scoped>
.home-container.is-embedded .header-container {
  display: none;
}
.home-container.is-embedded .content-container {
  height: 100vh;
}
</style>
