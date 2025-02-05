# LightEnderChest

### › Features

- Purchase of slots
- Customizable enderchest
- Set a fixed price for each slot or define a `price-step` for slots without an individual price
- Manage inventories and view cache statistics via the data menu
- Support for **Vault** and **PlayerPoints**
- Compatible with **MiniMessage** and **Legacy** color codes
- Database caching for enhanced performance

### › Abilities

- Purchase slots in another player's enderchest _(including the option to do so without deducting money)_
- Prohibition on buying slots in someone else's inventory
- Inventory editing is prohibited
- Close a slot in the viewed inventory using **SHIFT + RIGHT‑CLICK**.

### › Screenshots

![enderchest](https://github.com/user-attachments/assets/4c69b385-73b4-418d-826b-9e4398bb765f)

<details>
<summary>Data inventory</summary>

![data1](https://github.com/user-attachments/assets/bb0d4895-883e-495a-86c0-09337ab4ba1e)
![data2](https://github.com/user-attachments/assets/be8a2f27-c116-4123-9a6e-7c6ad9465594)

</details>

### › Commands and Permissions

| Command                             | Description                         | Permission                          |
|-------------------------------------|-------------------------------------|-------------------------------------|
| lightenderchest                     | Open your enderchest                | lightenderchest.use                 |
| lightenderchest clear               | Clear your enderchest               | lightenderchest.clear               |
|                                     |                                     |                                     |
| lightenderchest help                | Show a list of available commands   | lightenderchest.help                |
| lightenderchest \<username>         | Open another player's enderchest    | lightenderchest.admin.view          |
| lightenderchest open \<username>    | Open enderchest to the player       | lightenderchest.admin.open          |
| lightenderchest close \<username>   | Close enderchest to the player      | lightenderchest.admin.close         |
| lightenderchest clear \<username>   | Clear another player's enderchest   | lightenderchest.admin.clear         |
| lightenderchest reset \<username>   | Reset another player's enderchest   | lightenderchest.admin.reset         |
|                                     |                                     |                                     |
| lightenderchest cache data          | Open the cache data menu            | lightenderchest.cache.data          |
| lightenderchest cache refresh       | Refresh the cache from the database | lightenderchest.cache.refresh       |
| lightenderchest cache purge         | Purge outdated data from the cache  | lightenderchest.cache.purge         |
| lightenderchest cache clear-online  | Clear the cache for online players  | lightenderchest.cache.clear-online  |
| lightenderchest cache clear-offline | Clear the cache for offline players | lightenderchest.cache.clear-offline |
| lightenderchest cache clear         | Clear the entire cache              | lightenderchest.cache.clear         |
|                                     |                                     |                                     |
| lightenderchest reload              | Reload all configurations           | lightenderchest.reload              |

| Permission                 | Description                                   |
|----------------------------|-----------------------------------------------|
| lightenderchest.admin.edit | Edit other players' enderchests               |
| lightenderchest.admin.buy  | Purchase slots in other players' enderchests  |
| lightenderchest.cache.*    | Full access to cache management               |
| lightenderchest.admin.*    | Complete management of enderchests            |
| lightenderchest.*          | Full access                                   |